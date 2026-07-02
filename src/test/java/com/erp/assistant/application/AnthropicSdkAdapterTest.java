package com.erp.assistant.application;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCountTokensParams;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.MessageTokensCount;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawMessageStopEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.services.blocking.BetaService;
import com.anthropic.services.blocking.CompletionService;
import com.anthropic.services.blocking.MessageService;
import com.anthropic.services.blocking.ModelService;
import com.anthropic.services.blocking.messages.BatchService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnthropicSdkAdapter} against a fake {@link AnthropicClient} (no network). Covers
 * two seams that don't need a real SDK call:
 * <ul>
 *   <li>{@link AnthropicSdkAdapter#toParams} — the pure {@code ChatModelRequest} → {@code
 *       MessageCreateParams} mapping (role mapping, system prompt as a cache-controlled block, no
 *       temperature/top_p/top_k);</li>
 *   <li>{@link AnthropicSdkAdapter#stream} — a cancelled listener stops the loop early and does not
 *       receive {@code onEnd}.</li>
 * </ul>
 */
class AnthropicSdkAdapterTest {

    private static final AssistantProperties PROPERTIES =
            new AssistantProperties(true, "claude-opus-4-8", 4096, 8, null, null);

    // ---- toParams: pure mapping, no SDK call -------------------------------------------------

    @Test
    void toParamsMapsRolesAndSystemPromptWithoutSamplingParams() {
        AnthropicSdkAdapter adapter = new AnthropicSdkAdapter((AnthropicClient) null, PROPERTIES);
        ChatModelRequest request = new ChatModelRequest("You are ERP Copilot.", List.of(
                new ChatModelRequest.ChatMessage("user", List.of(ChatModelRequest.ContentBlock.text("hi"))),
                new ChatModelRequest.ChatMessage("assistant", List.of(ChatModelRequest.ContentBlock.text("hello")))));

        MessageCreateParams params = adapter.toParams(request);

        // Role mapping: user/assistant turns land as their corresponding MessageParam.Role.
        List<MessageParam> messages = params.messages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role().toString()).isEqualTo("user");
        assertThat(messages.get(0).content().asString()).isEqualTo("hi");
        assertThat(messages.get(1).role().toString()).isEqualTo("assistant");
        assertThat(messages.get(1).content().asString()).isEqualTo("hello");

        // System prompt sent as a cache-controlled text block, not the plain string overload.
        assertThat(params.system()).isPresent();
        assertThat(params.system().get().isTextBlockParams()).isTrue();
        var systemBlocks = params.system().get().asTextBlockParams();
        assertThat(systemBlocks).hasSize(1);
        assertThat(systemBlocks.get(0).text()).isEqualTo("You are ERP Copilot.");
        assertThat(systemBlocks.get(0).cacheControl()).isPresent();

        // Opus-4.8 request contract: no temperature/top_p/top_k (they 400 alongside adaptive thinking).
        assertThat(params.temperature()).isEmpty();
        assertThat(params.topP()).isEmpty();
        assertThat(params.topK()).isEmpty();
    }

    // ---- stream: cancellation stops the loop early -------------------------------------------

    @Test
    void streamStopsEarlyAndSkipsOnEndWhenListenerIsCancelled() {
        // Three no-op events (message_stop carries none of the fields the adapter reads); the listener
        // reports cancelled from the very first poll, so the adapter must break after the for-each loop's
        // first `next()` (Java's enhanced-for always pulls one element before running the loop body) and
        // must not call onEnd.
        RawMessageStreamEvent noopEvent = RawMessageStreamEvent.ofMessageStop(RawMessageStopEvent.builder().build());
        FakeStreamResponse fakeStream = new FakeStreamResponse(List.of(noopEvent, noopEvent, noopEvent));
        AnthropicClient client = new FakeAnthropicClient(fakeStream);
        AnthropicSdkAdapter adapter = new AnthropicSdkAdapter(client, PROPERTIES);

        CancelledListener listener = new CancelledListener();
        adapter.stream(new ChatModelRequest("system", List.of(
                new ChatModelRequest.ChatMessage("user", List.of(ChatModelRequest.ContentBlock.text("hi"))))),
                listener);

        assertThat(listener.endCalled).isFalse();
        assertThat(listener.errorCalled).isFalse();
        // Only the one element the for-each loop had to pull to enter its body; the other two never consumed.
        assertThat(fakeStream.consumedCount).isEqualTo(1);
        assertThat(fakeStream.closed).isTrue();
    }

    @Test
    void streamCallsOnEndWhenNotCancelled() {
        RawMessageStreamEvent noopEvent = RawMessageStreamEvent.ofMessageStop(RawMessageStopEvent.builder().build());
        FakeStreamResponse fakeStream = new FakeStreamResponse(List.of(noopEvent));
        AnthropicClient client = new FakeAnthropicClient(fakeStream);
        AnthropicSdkAdapter adapter = new AnthropicSdkAdapter(client, PROPERTIES);

        RecordingListener listener = new RecordingListener();
        adapter.stream(new ChatModelRequest("system", List.of(
                new ChatModelRequest.ChatMessage("user", List.of(ChatModelRequest.ContentBlock.text("hi"))))),
                listener);

        assertThat(listener.endCalled).isTrue();
        assertThat(listener.errorCalled).isFalse();
        assertThat(fakeStream.closed).isTrue();
    }

    // ---- fake listeners -------------------------------------------------------------------------

    private static final class CancelledListener implements AnthropicPort.ChatStreamListener {
        boolean endCalled;
        boolean errorCalled;

        @Override
        public void onTextDelta(String text) {}

        @Override
        public void onEnd(AnthropicPort.StopInfo stop) {
            endCalled = true;
        }

        @Override
        public void onError(Throwable error) {
            errorCalled = true;
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    }

    private static final class RecordingListener implements AnthropicPort.ChatStreamListener {
        boolean endCalled;
        boolean errorCalled;

        @Override
        public void onTextDelta(String text) {}

        @Override
        public void onEnd(AnthropicPort.StopInfo stop) {
            endCalled = true;
        }

        @Override
        public void onError(Throwable error) {
            errorCalled = true;
        }
    }

    // ---- fake SDK plumbing (no network) ----------------------------------------------------------

    /** Records how many events were pulled before the adapter's loop stopped, and whether it was closed. */
    private static final class FakeStreamResponse implements StreamResponse<RawMessageStreamEvent> {
        private final List<RawMessageStreamEvent> events;
        int consumedCount = 0;
        boolean closed = false;

        FakeStreamResponse(List<RawMessageStreamEvent> events) {
            this.events = events;
        }

        @Override
        public Stream<RawMessageStreamEvent> stream() {
            Iterator<RawMessageStreamEvent> delegate = events.iterator();
            Iterator<RawMessageStreamEvent> counting = new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return delegate.hasNext();
                }

                @Override
                public RawMessageStreamEvent next() {
                    if (!delegate.hasNext()) {
                        throw new NoSuchElementException();
                    }
                    RawMessageStreamEvent event = delegate.next();
                    consumedCount++;
                    return event;
                }
            };
            return StreamSupport.stream(
                    java.util.Spliterators.spliteratorUnknownSize(counting, 0), false);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakeAnthropicClient implements AnthropicClient {
        private final StreamResponse<RawMessageStreamEvent> streamResponse;

        FakeAnthropicClient(StreamResponse<RawMessageStreamEvent> streamResponse) {
            this.streamResponse = streamResponse;
        }

        @Override
        public AnthropicClientAsync async() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WithRawResponse withRawResponse() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnthropicClient withOptions(Consumer<com.anthropic.core.ClientOptions.Builder> consumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionService completions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageService messages() {
            return new FakeMessageService(streamResponse);
        }

        @Override
        public ModelService models() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BetaService beta() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            // no-op
        }
    }

    private static final class FakeMessageService implements MessageService {
        private final StreamResponse<RawMessageStreamEvent> streamResponse;

        FakeMessageService(StreamResponse<RawMessageStreamEvent> streamResponse) {
            this.streamResponse = streamResponse;
        }

        @Override
        public WithRawResponse withRawResponse() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageService withOptions(Consumer<com.anthropic.core.ClientOptions.Builder> consumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BatchService batches() {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.anthropic.models.messages.Message create(MessageCreateParams params, RequestOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageTokensCount countTokens(MessageCountTokensParams params, RequestOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamResponse<RawMessageStreamEvent> createStreaming(
                MessageCreateParams params, RequestOptions options) {
            return streamResponse;
        }
    }
}
