package api.samples.producerconsumer.records


class EventRecord<EK, EV>(val eventTopic: String, val key: EK, val value: EV)

class StateRecord<SK, SV>(val stateTopic: String, val key: SK, val value: SV)
