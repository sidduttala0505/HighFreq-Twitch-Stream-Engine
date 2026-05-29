package com.sidduttala.hypeclipper.detect;

import com.sidduttala.hypeclipper.dispatch.SpikeDispatcher;
import com.sidduttala.hypeclipper.model.ChatEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The detection half of the pipeline. Reads chat back off the topic and decides
 * whether anything worth clipping just happened.
 */
@Component
public class ChatEventConsumer {

    private final VelocityTrackingService velocity;
    private final SpikeDispatcher dispatcher;

    public ChatEventConsumer(VelocityTrackingService velocity, SpikeDispatcher dispatcher) {
        this.velocity = velocity;
        this.dispatcher = dispatcher;
    }

    @KafkaListener(topics = "${hype.topic}", groupId = "hype-detector")
    public void onChatEvent(ChatEvent event) {
        velocity.evaluate(event).ifPresent(dispatcher::onSpike);
    }
}
