package com.sidduttala.hypeclipper.ingest;

import com.sidduttala.hypeclipper.config.HypeProperties;
import com.sidduttala.hypeclipper.model.ChatEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes chat off the websocket thread and onto a topic.
 *
 * The socket reader's only job is to keep up with Twitch - if detection ever
 * gets slow, it backs up in Kafka instead of stalling the read loop and
 * getting us dropped from the channel. Keyed by channel so one channel's
 * messages stay ordered on one partition.
 */
@Component
public class ChatEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ChatEventProducer.class);

    private final KafkaTemplate<String, ChatEvent> kafka;
    private final HypeProperties props;

    public ChatEventProducer(KafkaTemplate<String, ChatEvent> kafka, HypeProperties props) {
        this.kafka = kafka;
        this.props = props;
    }

    public void publish(ChatEvent event) {
        kafka.send(props.getTopic(), event.channelId(), event)
                .exceptionally(err -> {
                    log.warn("failed to publish chat event: {}", err.getMessage());
                    return null;
                });
    }
}
