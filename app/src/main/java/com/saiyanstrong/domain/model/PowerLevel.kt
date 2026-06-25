package com.saiyanstrong.domain.model

enum class SaiyanStage(val label: String, val threshold: Int) {
    BASE("Base Saiyan",       0),
    SSJ1("Super Saiyan",      20_000),
    SSJ2("Super Saiyan 2",    50_000),
    SSJ3("Super Saiyan 3",    120_000),
    SSJ_GOD("Saiyan God",     300_000),
    ULTRA("Ultra Instinct",   750_000)
}

data class PowerLevel(
    val current: Int,
    val stage: SaiyanStage,
    val nextStageThreshold: Int,
    val progressToNext: Float
)
