package com.example.picsearch.data

import org.junit.Test
import org.junit.Assert.*

class SearchFilterTest {

    @Test
    fun `default filter is empty`() {
        val filter = SearchFilter()
        assertTrue(filter.isEmpty)
        assertEquals(0, filter.selectedCount)
    }

    @Test
    fun `timeRange only is not empty`() {
        val filter = SearchFilter(timeRange = TimeRange(0, 1000))
        assertFalse(filter.isEmpty)
        assertEquals(1, filter.selectedCount)
    }

    @Test
    fun `locationBounds only is not empty`() {
        val filter = SearchFilter(locationBounds = LocationBounds.fromBucket(39.9, 116.4))
        assertFalse(filter.isEmpty)
        assertEquals(1, filter.selectedCount)
    }

    @Test
    fun `sceneTags only is not empty`() {
        val filter = SearchFilter(sceneTags = listOf("海滩", "日落"))
        assertFalse(filter.isEmpty)
        assertEquals(2, filter.selectedCount)
    }

    @Test
    fun `all filters combined`() {
        val filter = SearchFilter(
            timeRange = TimeRange(0, 1000),
            locationBounds = LocationBounds.fromBucket(39.9, 116.4),
            sceneTags = listOf("海滩"),
        )
        assertFalse(filter.isEmpty)
        assertEquals(3, filter.selectedCount)
    }

    @Test
    fun `empty sceneTags list does not count`() {
        val filter = SearchFilter(
            timeRange = TimeRange(0, 1000),
            sceneTags = emptyList(),
        )
        assertEquals(1, filter.selectedCount)
    }

    @Test
    fun `LocationBounds fromBucket computes correct range`() {
        val bounds = LocationBounds.fromBucket(39.9, 116.4)
        assertEquals(39.85, bounds.latMin, 0.001)
        assertEquals(39.95, bounds.latMax, 0.001)
        assertEquals(116.35, bounds.lonMin, 0.001)
        assertEquals(116.45, bounds.lonMax, 0.001)
    }

    @Test
    fun `LocationCluster displayName with readableName`() {
        val cluster = LocationCluster(
            latBucket = 39.9,
            lonBucket = 116.4,
            centerLat = 39.9,
            centerLon = 116.4,
            count = 42,
            readableName = "北京",
        )
        assertEquals("北京", cluster.displayName)
    }

    @Test
    fun `LocationCluster displayName fallback`() {
        val cluster = LocationCluster(
            latBucket = 39.9,
            lonBucket = 116.4,
            centerLat = 39.9,
            centerLon = 116.4,
            count = 42,
        )
        assertTrue(cluster.displayName.contains("42"))
        assertTrue(cluster.displayName.contains("N"))
        assertTrue(cluster.displayName.contains("E"))
    }

    @Test
    fun `LocationCluster southernHemisphere westHemisphere`() {
        val cluster = LocationCluster(
            latBucket = -33.9,
            lonBucket = -18.4,
            centerLat = -33.9,
            centerLon = -18.4,
            count = 10,
        )
        assertTrue(cluster.displayName.contains("S"))
        assertTrue(cluster.displayName.contains("W"))
    }
}
