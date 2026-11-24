package varify.config

import varify.event.*

data class VarifyConfiguration(
    val variationAttemptsLimit: Int = 100,
    val rejectedAttemptsLimit: Int = 500,
    val shrinkAttemptsLimit: Int = 1000,
    val randomizer: Randomizer = Randomizer.System(),
    val eventListeners: List<VarifyEventListener> = emptyList(),
) {
    fun withEventListener(listener: VarifyEventListener): VarifyConfiguration =
        copy(eventListeners = eventListeners + listener)
}
