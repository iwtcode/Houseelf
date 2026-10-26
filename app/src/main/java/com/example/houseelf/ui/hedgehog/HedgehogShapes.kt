package com.example.houseelf.ui.hedgehog

import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

// Контуры из SVG-рисунка в координатах 400×400. Разбираются один раз.
internal val spinesOuter by lazy {
    svgPath("M88.5 277.7 L65.9 266.3 L86.0 250.4 L67.1 234.0 L89.1 223.2 L71.8 201.8 L97.8 197.4 L87.5 174.0 L111.4 174.3 L104.6 147.3 L129.4 155.0 L128.7 127.8 L150.9 140.5 L156.3 115.4 L174.8 131.5 L184.9 106.0 L200.0 128.5 L214.8 108.4 L225.2 131.5 L244.3 113.5 L249.1 140.5 L270.5 129.2 L270.6 155.0 L293.9 149.0 L288.6 174.3 L313.9 173.0 L302.2 197.4 L329.0 201.5 L310.9 223.2 L332.8 234.0 L314.0 250.4 L333.6 266.3 L311.5 277.7 L200 302 Z")
}

internal val spinesInner by lazy {
    svgPath("M107.2 273.9 L87.5 260.9 L106.6 247.0 L89.5 228.6 L112.5 220.9 L101.8 199.1 L124.6 197.3 L120.1 173.8 L142.0 178.1 L142.3 152.0 L163.5 164.4 L171.1 143.2 L187.5 157.4 L200.0 135.5 L212.5 157.4 L229.2 142.2 L236.5 164.4 L256.1 155.1 L258.0 178.1 L279.1 174.7 L275.4 197.3 L297.2 199.6 L287.5 220.9 L310.0 228.7 L293.4 247.0 L310.7 260.9 L292.8 273.9 L200 308 Z")
}

internal val faceTop by lazy {
    svgPath("M122.2 223.4 L128.3 212.5 L136.6 202.6 L146.8 194.1 L158.6 187.2 L171.7 182.2 L185.6 179.0 L200.0 178.0 L214.4 179.0 L228.3 182.2 L241.4 187.2 L253.2 194.1 L263.4 202.6 L271.7 212.5 L277.8 223.4 L267.7 226.5 L249.9 223.0 L255.2 208.4 L237.0 210.2 L236.0 195.0 L219.7 201.9 L212.5 187.9 L200.0 199.0 L187.5 187.9 L180.3 201.9 L164.0 195.0 L163.0 210.2 L144.8 208.4 L150.1 223.0 L132.3 226.5 Z")
}

internal val pocketBody by lazy { svgPath("M82 306h236v44a26 26 0 0 1-26 26H108a26 26 0 0 1-26-26z") }

internal val pocketStitchBody by lazy { svgPath("M96 322h208v28a16 16 0 0 1-16 16H112a16 16 0 0 1-16-16z") }

internal val pocketStitchLip by lazy { svgPath("M90 298h220") }

internal val toes by lazy { svgPath("M152 284v6M158 283v7M164 284v6M236 284v6M242 283v7M248 284v6") }

internal val browsHappy by lazy { svgPath("M153 227q13-8 26 0M221 227q13-8 26 0") }

internal val browsSad by lazy { svgPath("M153 230q13-3 26-9M247 230q-13-3-26-9") }

internal val browsThinking by lazy { svgPath("M153 227q13-8 26 0M221 220q13-8 26 0") }

internal val mouthHappy by lazy { svgPath("M200 265v4M200 269q-5 6-11 3M200 269q5 6 11 3") }

internal val mouthSad by lazy { svgPath("M200 265v3M200 268q-5-5-11-2M200 268q5-5 11-2") }

internal val mouthThinking by lazy { svgPath("M200 265v2") }

internal val stickerLines1 by lazy { svgPath("M99 171h20M99 178h14M99 185h17") }

internal val stickerCheck by lazy { svgPath("M284 170l6 6 12-13") }

internal val stickerLines2 by lazy { svgPath("M188 120h20M188 127h14M188 134h17") }
