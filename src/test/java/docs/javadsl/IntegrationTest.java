/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) since 2016 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.Cancellable;

import org.apache.pekko.actor.ActorSystem;

// #publish-single
import org.apache.pekko.stream.connectors.googlecloud.pubsub.grpc.PubSubSettings;
import org.apache.pekko.stream.connectors.googlecloud.pubsub.grpc.javadsl.GooglePubSub;
import org.apache.pekko.stream.connectors.googlecloud.pubsub.grpc.javadsl.GrpcPublisher;
import org.apache.pekko.stream.connectors.googlecloud.pubsub.grpc.javadsl.PubSubAttributes;
import org.apache.pekko.stream.connectors.testkit.javadsl.LogCapturingJunit4;
import org.apache.pekko.stream.javadsl.*;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.*;

// #publish-single

import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IntegrationTest {
  @Rule public final LogCapturingJunit4 logCapturing = new LogCapturingJunit4();

  static final ActorSystem system = ActorSystem.create("IntegrationTest");

  @Test
  public void shouldPublishAMessage()
      throws InterruptedException, ExecutionException, TimeoutException {
    // #publish-single
    final String projectId = "pekko-connectors";
    final String topic = "simpleTopic";

    final PubsubMessage publishMessage =
        PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8("Hello world!")).build();

    final PublishRequest publishRequest =
        PublishRequest.newBuilder()
            .setTopic("projects/" + projectId + "/topics/" + topic)
            .addMessages(publishMessage)
            .build();

    final Source<PublishRequest, NotUsed> source = Source.single(publishRequest);

    final Flow<PublishRequest, PublishResponse, NotUsed> publishFlow = GooglePubSub.publish(1);

    final CompletionStage<List<PublishResponse>> publishedMessageIds =
        source.via(publishFlow).runWith(Sink.seq(), system);
    // #publish-single

    assertTrue(
        "number of published messages should be more than 0",
        publishedMessageIds.toCompletableFuture().get(2, TimeUnit.SECONDS).size() > 0);
  }

  @Test
  public void shouldPublishBatch()
      throws InterruptedException, ExecutionException, TimeoutException {
    // #publish-fast
    final String projectId = "pekko-connectors";
    final String topic = "simpleTopic";

    final PubsubMessage publishMessage =
        PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8("Hello world!")).build();

    final Source<PubsubMessage, NotUsed> messageSource = Source.single(publishMessage);
    final CompletionStage<List<PublishResponse>> published =
        messageSource
            .groupedWithin(1000, Duration.ofMinutes(1))
            .map(
                messages ->
                    PublishRequest.newBuilder()
                        .setTopic("projects/" + projectId + "/topics/" + topic)
                        .addAllMessages(messages)
                        .build())
            .via(GooglePubSub.publish(1))
            .runWith(Sink.seq(), system);
    // #publish-fast

    assertTrue(
        "number of published messages should be more than 0",
        published.toCompletableFuture().get(2, TimeUnit.SECONDS).size() > 0);
  }

  @Test
  public void shouldSubscribeStream()
      throws InterruptedException, ExecutionException, TimeoutException {
    // #subscribe-stream
    final String projectId = "pekko-connectors";
    final String subscription = "simpleSubscription";

    final StreamingPullRequest request =
        StreamingPullRequest.newBuilder()
            .setSubscription("projects/" + projectId + "/subscriptions/" + subscription)
            .setStreamAckDeadlineSeconds(10)
            .build();

    final Duration pollInterval = Duration.ofSeconds(1);
    final Source<ReceivedMessage, CompletableFuture<Cancellable>> subscriptionSource =
        GooglePubSub.subscribe(request, pollInterval);
    // #subscribe-stream

    final CompletionStage<ReceivedMessage> first = subscriptionSource.runWith(Sink.head(), system);

    final String topic = "simpleTopic";
    final ByteString msg = ByteString.copyFromUtf8("Hello world!");

    final PubsubMessage publishMessage = PubsubMessage.newBuilder().setData(msg).build();

    final PublishRequest publishRequest =
        PublishRequest.newBuilder()
            .setTopic("projects/" + projectId + "/topics/" + topic)
            .addMessages(publishMessage)
            .build();

    Source.single(publishRequest).via(GooglePubSub.publish(1)).runWith(Sink.ignore(), system);

    assertEquals(
        "received and expected messages not the same",
        msg,
        first.toCompletableFuture().get(2, TimeUnit.SECONDS).getMessage().getData());
  }

  @Test
  public void shouldSubscribeSync()
      throws InterruptedException, ExecutionException, TimeoutException {
    // #subscribe-sync
    final String projectId = "pekko-connectors";
    final String subscription = "simpleSubscription";

    final PullRequest request =
        PullRequest.newBuilder()
            .setSubscription("projects/" + projectId + "/subscriptions/" + subscription)
            .setMaxMessages(10)
            .build();

    final Duration pollInterval = Duration.ofSeconds(1);
    final Source<ReceivedMessage, CompletableFuture<Cancellable>> subscriptionSource =
        GooglePubSub.subscribePolling(request, pollInterval);
    // #subscribe-sync

    final CompletionStage<ReceivedMessage> first = subscriptionSource.runWith(Sink.head(), system);

    final String topic = "simpleTopic";
    final ByteString msg = ByteString.copyFromUtf8("Hello world!");

    final PubsubMessage publishMessage = PubsubMessage.newBuilder().setData(msg).build();

    final PublishRequest publishRequest =
        PublishRequest.newBuilder()
            .setTopic("projects/" + projectId + "/topics/" + topic)
            .addMessages(publishMessage)
            .build();

    Source.single(publishRequest).via(GooglePubSub.publish(1)).runWith(Sink.ignore(), system);

    assertEquals(
        "received and expected messages not the same",
        msg,
        first.toCompletableFuture().get(2, TimeUnit.SECONDS).getMessage().getData());
  }

  @Test
  public void shouldAcknowledge() {
    final String projectId = "pekko-connectors";
    final String subscription = "simpleSubscription";

    final StreamingPullRequest request =
        StreamingPullRequest.newBuilder()
            .setSubscription("projects/" + projectId + "/subscriptions/" + subscription)
            .setStreamAckDeadlineSeconds(10)
            .build();

    final Duration pollInterval = Duration.ofSeconds(1);
    final Source<ReceivedMessage, CompletableFuture<Cancellable>> subscriptionSource =
        GooglePubSub.subscribe(request, pollInterval);

    // #acknowledge
    final Sink<AcknowledgeRequest, CompletionStage<Done>> ackSink = GooglePubSub.acknowledge(1);

    subscriptionSource
        .map(
            receivedMessage -> {
              // do some computation
              return receivedMessage.getAckId();
            })
        .groupedWithin(10, Duration.ofSeconds(1))
        .map(acks -> AcknowledgeRequest.newBuilder().addAllAckIds(acks).build())
        .to(ackSink);
    // #acknowledge
  }

  @Test
  public void customPublisher() {
    // #attributes
    final PubSubSettings settings = PubSubSettings.create(system);
    final GrpcPublisher publisher = GrpcPublisher.create(settings, system);

    final Flow<PublishRequest, PublishResponse, NotUsed> publishFlow =
        GooglePubSub.publish(1).withAttributes(PubSubAttributes.publisher(publisher));
    // #attributes
  }

  @AfterClass
  public static void tearDown() {
    system.terminate();
  }
}
