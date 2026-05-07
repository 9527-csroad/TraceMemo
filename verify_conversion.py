from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Optional

import numpy as np
import torch
from PIL import Image

import cn_clip.clip as clip
import cn_clip.clip.utils as clip_utils
from cn_clip.clip import load_from_name


def _l2n(x: torch.Tensor) -> torch.Tensor:
    return x / (x.norm(dim=-1, keepdim=True) + 1e-12)


def _fmt5(x: torch.Tensor) -> str:
    v = x.detach().cpu().reshape(-1)[:5].tolist()
    return " ".join([f"{t:.6f}" for t in v])


def _cos(a: torch.Tensor, b: torch.Tensor) -> float:
    return float(torch.nn.functional.cosine_similarity(_l2n(a), _l2n(b)).item())


def _ncnn_run(param_path: Path, bin_path: Path, inputs: dict, out_name: str) -> torch.Tensor:
    import ncnn

    if not param_path.exists() or not bin_path.exists():
        raise FileNotFoundError(f"{param_path} / {bin_path}")

    with ncnn.Net() as net:
        net.opt.num_threads = 1
        net.opt.use_vulkan_compute = False
        if net.load_param(str(param_path)) != 0:
            raise RuntimeError(f"load_param failed: {param_path}")
        if net.load_model(str(bin_path)) != 0:
            raise RuntimeError(f"load_model failed: {bin_path}")
        with net.create_extractor() as ex:
            for name, arr in inputs.items():
                ex.input(name, ncnn.Mat(np.ascontiguousarray(arr)).clone())
            ret, out0 = ex.extract(out_name)
            if ret != 0:
                raise RuntimeError(f"extract {out_name} failed: {ret}")
            return torch.from_numpy(np.array(out0))


def _resolve_ncnn_pair(ncnn_dir: Path, prefix: str, kind: str) -> tuple[Path, Path]:
    p = ncnn_dir / f"{prefix}.{kind}.param"
    b = ncnn_dir / f"{prefix}.{kind}.bin"
    if p.exists() and b.exists():
        return p, b
    candidates = sorted(ncnn_dir.glob(f"*.{kind}.param"))
    if candidates:
        param = candidates[0]
        return param, param.with_suffix(".bin")
    raise FileNotFoundError(f"no *.{kind}.param under {ncnn_dir}")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--arch", default="RN50")
    ap.add_argument("--ncnn-dir", required=True, help="directory containing *.img.* and *.txt.*")
    ap.add_argument("--prefix", default=None, help="file prefix; auto if omitted")
    ap.add_argument("--ckpt-root", default=None, help="directory holding chinese-clip-rn50/ etc.; auto if omitted")
    ap.add_argument("--image", required=True)
    ap.add_argument("--text", default="奶茶")
    ap.add_argument("--ctx", type=int, default=52)
    args = ap.parse_args()

    ncnn_dir = Path(args.ncnn_dir).resolve()
    prefix = args.prefix or f"chinese_clip_{args.arch.replace('-', '_').lower()}"

    ckpt_root = args.ckpt_root
    if not ckpt_root:
        ckpt_root = str(Path(__file__).resolve().parent / "Chinese-CLIP")

    print(f"--- load PyTorch {args.arch} ---")
    model, preprocess = load_from_name(args.arch, device="cpu", download_root=ckpt_root)
    model.eval()

    img_pil = Image.open(args.image).convert("RGB")
    img_tensor = preprocess(img_pil).unsqueeze(0)
    tok = clip.tokenize([args.text], context_length=args.ctx)

    with torch.no_grad():
        img_pt = model.encode_image(img_tensor)
        txt_pt = model.encode_text(tok)

    img_param, img_bin = _resolve_ncnn_pair(ncnn_dir, prefix, "img")
    txt_param, txt_bin = _resolve_ncnn_pair(ncnn_dir, prefix, "txt")

    print(f"--- NCNN vision: {img_param.name} ---")
    img_ncnn = _ncnn_run(
        img_param, img_bin,
        {"in0": img_tensor[0].numpy().astype(np.float32)},
        "out0",
    ).reshape(1, -1)

    print(f"--- NCNN text: {txt_param.name} ---")
    ids_i32 = tok[0].detach().cpu().numpy().astype(np.int32)
    mask = (tok[0] != 0).float().numpy().astype(np.float32)
    txt_ncnn = _ncnn_run(
        txt_param, txt_bin,
        {"in0": ids_i32, "in1": mask},
        "out0",
    ).reshape(1, -1)

    cos_v = _cos(img_pt, img_ncnn)
    cos_t = _cos(txt_pt, txt_ncnn)

    print()
    print("=" * 60)
    print(f"arch             : {args.arch}")
    print(f"vision dim       : {img_ncnn.shape[1]}")
    print(f"text   dim       : {txt_ncnn.shape[1]}")
    print(f"Vision CosSim    : {cos_v:.6f}  {'OK' if cos_v > 0.99 else 'BAD'}")
    print(f"Text   CosSim    : {cos_t:.6f}  {'OK' if cos_t > 0.99 else 'BAD'}")
    print("-" * 60)
    print(f"PT   vision (L2) : {_fmt5(_l2n(img_pt))}")
    print(f"NCNN vision (L2) : {_fmt5(_l2n(img_ncnn))}")
    print(f"PT   text   (L2) : {_fmt5(_l2n(txt_pt))}")
    print(f"NCNN text   (L2) : {_fmt5(_l2n(txt_ncnn))}")

    pt_text_image = _cos(img_pt, txt_pt)
    nc_text_image = _cos(img_ncnn, txt_ncnn)
    print("-" * 60)
    print(f"PT   img-txt sim : {pt_text_image:.6f}")
    print(f"NCNN img-txt sim : {nc_text_image:.6f}")
    print("=" * 60)


if __name__ == "__main__":
    main()
