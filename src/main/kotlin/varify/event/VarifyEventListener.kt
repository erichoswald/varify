package varify.event

fun interface VarifyEventListener {
    fun onEvent(event: VarifyEvent)
}

fun List<VarifyEventListener>.publish(event: VarifyEvent) {
    forEach { it.onEvent(event) }
}
