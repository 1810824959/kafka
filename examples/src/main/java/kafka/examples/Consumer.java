/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kafka.examples;

import kafka.utils.ShutdownableThread;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class Consumer extends ShutdownableThread {
    private final KafkaConsumer<Integer, String> consumer;
    private final String topic;
    private final String groupId;
    private final int numMessageToConsume;
    private int messageRemaining;
    private final CountDownLatch latch;

    public Consumer(final String topic,
                    final String groupId,
                    final Optional<String> instanceId,
                    final boolean readCommitted,
                    final int numMessageToConsume,
                    final CountDownLatch latch) {
        super("KafkaConsumerExample", false);
        this.groupId = groupId;
        Properties props = new Properties();

        // broker的地址
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaProperties.KAFKA_SERVER_URL + ":" + KafkaProperties.KAFKA_SERVER_PORT);
        // group id ? 不指定会怎么办？
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // group instance id 消费者实例id
        /**
         * A unique identifier of the consumer instance provided by end user.
         * Only non-empty strings are permitted.
         * If set, the consumer is treated as a static member
         * which means that only one instance with this ID is allowed in the consumer group at any time.
         * This can be used in combination with a larger session timeout to avoid group rebalances
         * caused by transient unavailability (e.g. process restarts).
         * If not set, the consumer will join the group as a dynamic member, which is the traditional behavior.
         */
        // 静态消费组成员，不会因为超时，频繁触发 rebalance
        instanceId.ifPresent(id -> props.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, id));

        // offset 是否自动提交
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        // 配合上面一个配置， 默认多少时间自动提交一次
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");

        // 会话超时时长，consumer挂掉了
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        // key/value 的反序列化类
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.IntegerDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");

        // 读已提交，事务的隔离级别
        if (readCommitted) {
            props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        }
        //重置消费偏移量策略
        //当偏移量没有初始值时或者参数非法时，比如数据被删除时的重置策略
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new KafkaConsumer<>(props);
        this.topic = topic;
        this.numMessageToConsume = numMessageToConsume;
        this.messageRemaining = numMessageToConsume;
        this.latch = latch;
    }

    KafkaConsumer<Integer, String> get() {
        return consumer;
    }

    @Override
    public void doWork() {
        consumer.subscribe(Collections.singletonList(this.topic));
        ConsumerRecords<Integer, String> records = consumer.poll(Duration.ofSeconds(1));
        for (ConsumerRecord<Integer, String> record : records) {
            System.out.println(groupId + " received message : from partition " + record.partition() + ", (" + record.key() + ", " + record.value() + ") at offset " + record.offset());
        }
        messageRemaining -= records.count();
        if (messageRemaining <= 0) {
            System.out.println(groupId + " finished reading " + numMessageToConsume + " messages");
            latch.countDown();
        }
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }
}
