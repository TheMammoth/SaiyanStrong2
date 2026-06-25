package com.saiyanstrong.data.local.seed

import com.saiyanstrong.data.local.entity.ExerciseEntity

object ExerciseSeeder {
    val DATA = listOf(
        ExerciseEntity(1, "Barbell Squat", "SQUAT",
            "QUADRICEPS,GLUTEUS_MAXIMUS,ERECTOR_SPINAE",
            "HAMSTRINGS,CALVES,RECTUS_ABDOMINIS",
            "squat_transition.json", "muscle_squat"),
        ExerciseEntity(2, "Deadlift", "HINGE",
            "ERECTOR_SPINAE,GLUTEUS_MAXIMUS,HAMSTRINGS",
            "TRAPEZIUS,LATISSIMUS_DORSI,QUADRICEPS",
            "deadlift_transition.json", "muscle_deadlift"),
        ExerciseEntity(3, "Bench Press", "PUSH",
            "PECTORALIS_MAJOR,DELTOIDS,TRICEPS",
            "BICEPS,RECTUS_ABDOMINIS",
            "bench_transition.json", "muscle_bench"),
        ExerciseEntity(4, "Overhead Press", "PUSH",
            "DELTOIDS,TRICEPS",
            "TRAPEZIUS,ERECTOR_SPINAE,RECTUS_ABDOMINIS",
            "ohp_transition.json", "muscle_ohp")
    )
}
