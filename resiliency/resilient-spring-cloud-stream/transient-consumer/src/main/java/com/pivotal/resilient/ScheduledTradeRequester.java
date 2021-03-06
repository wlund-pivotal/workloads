package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
@EnableBinding(ScheduledTradeRequester.MessagingBridge.class)
@EnableScheduling
@ConditionalOnProperty(name="scheduledTradeRequester", matchIfMissing = false)
public class ScheduledTradeRequester {
    private final Logger logger = LoggerFactory.getLogger(ScheduledTradeRequester.class);
    private Random accountRandomizer = new Random(System.currentTimeMillis());

    interface MessagingBridge {

        String OUTBOUND_TRADE_REQUESTS = "outboundTradeRequests";

        @Output(OUTBOUND_TRADE_REQUESTS)
        MessageChannel outboundTradeRequests();
    }

    @Autowired private MessagingBridge messagingBridge;

    public ScheduledTradeRequester() {
        logger.info("Created");
    }

    private volatile long tradeSequence = 1;
    private volatile long sentTradeCount = 0;

    @Scheduled(fixedDelayString = "${tradeRateMs:1000}")
    public void produceTradeRequest() {
        long tradeId = tradeSequence++;
        String body = String.format("Trade %d", tradeId);
        long account = accountRandomizer.nextInt(10);

        logger.info("[sent:{}] Requesting {} for account {}", sentTradeCount, body, account);

        // send() always return true so we cannot use it to determine a successful send
        messagingBridge.outboundTradeRequests().send(
                MessageBuilder.withPayload(body)
                        .setHeader("tradeId", tradeId)
                        .setHeader("account", account).build());

        sentTradeCount++;
    }
}
