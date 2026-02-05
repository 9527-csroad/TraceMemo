from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
from pathlib import Path
from typing import Optional, Sequence, Tuple

import torch

import cn_clip.clip.utils as clip_utils
import cn_clip.clip as clip
from cn_clip.clip.bert_tokenizer import default_vocab


class ImageEncoder(torch.nn.Module):
    def __init__(self, model: torch.nn.Module):
        super().__init__()
        self.model = model

    def forward(self, image: torch.Tensor) -> torch.Tensor:
        return self.model.encode_image(image)


class TextEncoder(torch.nn.Module):
    def __init__(self, model: torch.nn.Module):
        super().__init__()
        self.model = model

    def forward(self, text: torch.Tensor) -> torch.Tensor:
        return self.model.encode_text(text)


def _find_exe(name: str, tools_dir: Optional[str]) -> str:
    candidates = [name]
    if os.name == "nt" and not name.lower().endswith(".exe"):
        candidates.insert(0, name + ".exe")

    if tools_dir:
        for n in candidates:
            p = Path(tools_dir) / n
            if p.is_file():
                return str(p)

    for n in candidates:
        p = shutil.which(n)
        if p:
            return p

    raise FileNotFoundError(name)


def _run(cmd: Sequence[str]) -> None:
    subprocess.run(list(cmd), check=True)


def _default_ckpt(model_arch: str) -> str:
    repo_root = Path(__file__).resolve().parent
    reponame, filename = clip_utils._MODELS[model_arch]
    p = repo_root / reponame / filename
    if not p.is_file():
        raise FileNotFoundError(str(p))
    return str(p)


def _load_model(model_arch: str, ckpt_path: str) -> torch.nn.Module:
    struct = clip_utils._MODEL_INFO[model_arch]["struct"]
    with open(ckpt_path, "rb") as f:
        checkpoint = torch.load(f, map_location="cpu")
    model = clip_utils.create_model(struct, checkpoint).float().eval()
    return model


def _export_onnx(
    model: torch.nn.Module,
    model_arch: str,
    out_prefix: str,
    export_vision: bool,
    export_text: bool,
    context_length: int,
    opset: int,
) -> tuple[Optional[str], Optional[str]]:
    out_img = None
    out_txt = None

    resolution = clip_utils._MODEL_INFO[model_arch]["input_resolution"]
    if export_vision:
        out_img = f"{out_prefix}.img.onnx"
        dummy_image = torch.randn(1, 3, resolution, resolution, dtype=torch.float32)
        torch.onnx.export(
            ImageEncoder(model),
            (dummy_image,),
            out_img,
            input_names=["image"],
            output_names=["image_features"],
            export_params=True,
            do_constant_folding=False,
            opset_version=opset
        )

    if export_text:
        out_txt = f"{out_prefix}.txt.onnx"
        dummy_text = clip.tokenize([""], context_length=context_length)
        torch.onnx.export(
            TextEncoder(model),
            (dummy_text,),
            out_txt,
            input_names=["text"],
            output_names=["text_features"],
            export_params=True,
            do_constant_folding=False,
            opset_version=opset,
        )

    return out_img, out_txt


def _onnx_to_ncnn(onnx2ncnn: str, onnx_path: str, out_prefix: str) -> tuple[str, str]:
    out_param = f"{out_prefix}.param"
    out_bin = f"{out_prefix}.bin"
    _run([onnx2ncnn, onnx_path, out_param, out_bin])
    return out_param, out_bin


def _pnnx_export(model: torch.nn.Module, pt_path: str, inputs: Tuple[torch.Tensor, ...]) -> tuple[str, str]:
    try:
        import pnnx  # type: ignore
    except Exception:
        raise ImportError("pnnx")
    base = os.path.splitext(pt_path)[0]
    param = base + ".ncnn.param"
    bin_path = base + ".ncnn.bin"
    try:
        pnnx.export(model, pt_path, inputs)
    except SyntaxError:
        if os.path.isfile(param) and os.path.isfile(bin_path):
            return param, bin_path
        raise
    except Exception:
        if os.path.isfile(param) and os.path.isfile(bin_path):
            return param, bin_path
        raise
    if not (os.path.isfile(param) and os.path.isfile(bin_path)):
        raise FileNotFoundError(base)
    return param, bin_path


def _optimize_ncnn(
    ncnnoptimize: str,
    in_param: str,
    in_bin: str,
    out_prefix: str,
    opt_flag: int,
) -> tuple[str, str]:
    out_param = f"{out_prefix}.opt.param"
    out_bin = f"{out_prefix}.opt.bin"
    _run([ncnnoptimize, in_param, in_bin, out_param, out_bin, str(opt_flag)])
    return out_param, out_bin


def _embed_dim(model: torch.nn.Module) -> int:
    return int(model.text_projection.shape[1])


def _write_encoder_meta(meta_path: str, meta: dict) -> None:
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)


def _normalize_archs(raw: Sequence[str]) -> list[str]:
    out: list[str] = []
    for s in raw:
        s = str(s).strip()
        if not s:
            continue
        if s[0] in "{[" and s[-1] in "}]":
            s = s[1:-1].strip()
        s = s.replace('"', "").replace("'", "")
        parts = [p.strip() for p in s.split(",") if p.strip()]
        out.extend(parts if parts else [s])
    return out


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument(
        "--model-arch",
        required=True,
        type=str,
    )
    p.add_argument("--pytorch-ckpt-path", default=None, type=str)
    p.add_argument("--out-dir", required=True, type=str)
    p.add_argument("--out-prefix", default=None, type=str)
    p.add_argument("--convert-vision", action="store_true")
    p.add_argument("--convert-text", action="store_true")
    p.add_argument("--context-length", type=int, default=52)
    p.add_argument("--ncnn-tools-dir", default=None, type=str)
    p.add_argument("--enable-optimize", action="store_true")
    p.add_argument("--ncnn-opt-flag", type=int, default=65536)
    args = p.parse_args()

    if not args.convert_vision and not args.convert_text:
        args.convert_vision = True
        args.convert_text = True

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    ncnnoptimize = None
    if args.enable_optimize:
        ncnnoptimize = _find_exe("ncnnoptimize", args.ncnn_tools_dir)

    archs = _normalize_archs([args.model_arch])
    allowed_archs = {"ViT-B-16", "ViT-L-14", "ViT-L-14-336", "ViT-H-14", "RN50"}
    bad = [a for a in archs if a not in allowed_archs]
    if bad:
        p.error(f"argument --model-arch: invalid choice: {bad[0]!r} (choose from {', '.join(sorted(allowed_archs))})")
    if len(archs) != 1:
        raise ValueError("--model-arch 仅支持单个模型")

    vocab_path = str(Path(default_vocab()).resolve())

    arch = archs[0]
    ckpt_path = args.pytorch_ckpt_path or _default_ckpt(arch)
    base_prefix = args.out_prefix or "chinese_clip"
    out_prefix = str(out_dir / f"{base_prefix}_{arch.replace('-', '_').lower()}")

    model = _load_model(arch, ckpt_path)

    dim = _embed_dim(model)
    resolution = int(clip_utils._MODEL_INFO[arch]["input_resolution"])

    if args.convert_vision:
        in_param = None
        in_bin = None
        pt_path = f"{out_prefix}.img.pnnx.pt"
        dummy_image = torch.randn(1, 3, resolution, resolution, dtype=torch.float32)
        in_param, in_bin = _pnnx_export(ImageEncoder(model).eval(), pt_path, (dummy_image,))
        if ncnnoptimize and in_param and in_bin:
            _optimize_ncnn(ncnnoptimize, in_param, in_bin, f"{out_prefix}.img", args.ncnn_opt_flag)
        _write_encoder_meta(
            f"{out_prefix}.img.meta.json",
            {
                "model_arch": arch,
                "encoder": "image",
                "input_name": "image",
                "input_shape": [1, 3, resolution, resolution],
                "output_name": "image_features",
                "output_shape": [1, dim],
            },
        )

    if args.convert_text:
        in_param = None
        in_bin = None
        pt_path = f"{out_prefix}.txt.pnnx.pt"
        dummy_text = clip.tokenize([""], context_length=args.context_length).to(torch.int32)
        in_param, in_bin = _pnnx_export(TextEncoder(model).eval(), pt_path, (dummy_text,))
        if ncnnoptimize and in_param and in_bin:
            _optimize_ncnn(ncnnoptimize, in_param, in_bin, f"{out_prefix}.txt", args.ncnn_opt_flag)
        _write_encoder_meta(
            f"{out_prefix}.txt.meta.json",
            {
                "model_arch": arch,
                "encoder": "text",
                "input_name": "text",
                "seq_length": int(args.context_length),
                "output_name": "text_features",
                "output_shape": [1, dim],
                "vocab_file": vocab_path,
            },
        )


if __name__ == "__main__":
    main()
