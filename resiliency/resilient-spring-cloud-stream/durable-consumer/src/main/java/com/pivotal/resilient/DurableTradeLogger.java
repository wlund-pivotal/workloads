package com.pivotal.resilient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Service
@EnableBinding(DurableTradeLogger.MessagingBridge.class)
@ConditionalOnProperty(name="durableTradeLogger", matchIfMissing = false)
public class DurableTradeLogger {
    private final Logger logger = LoggerFactory.getLogger(DurableTradeLogger.class);

    @Autowired private MessagingBridge messagingBridge;

    interface MessagingBridge {

        String INPUT = "durable-trade-logger-input";

        @Input(INPUT)
        SubscribableChannel input();

    }

    @Value("${processingTime:1s}")
    private Duration processingTime;

    private volatile long receivedTradeCount;
    private AtomicLong firstTradeId = new AtomicLong();

    public DurableTradeLogger() {
        logger.info("Created");
    }

    @StreamListener(MessagingBridge.INPUT)
    public void execute(@Header("account") long account,
                        @Header("tradeId") long tradeId,
                        @Payload String trade) {

        firstTradeId.compareAndSet(0, tradeId);
        long missedTrades = tradeId - firstTradeId.get() - receivedTradeCount;
        receivedTradeCount++;

        String tradeConfirm = String.format("[total:%d,missed:%d] %s (account: %d) done",
                receivedTradeCount,
                missedTrades,
                trade, account);
        logger.info("Received {}", tradeConfirm);
        try {
            Thread.sleep(processingTime.toMillis());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            logger.info("Processed {}", tradeConfirm);
        }
    }

}
