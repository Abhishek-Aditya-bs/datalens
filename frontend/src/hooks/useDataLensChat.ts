import { useState, useCallback, useRef, useEffect } from 'react';

export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  toolCalls?: ToolCall[];
  isStreaming?: boolean;
}

export interface ToolCall {
  id: string;
  name: string;
  args: Record<string, unknown>;
  result?: string; // JSON string
  status: 'pending' | 'complete' | 'error';
}

interface UseDataLensChatOptions {
  apiUrl?: string;
  sessionId?: string;
}

export function useDataLensChat(options: UseDataLensChatOptions = {}) {
  const { apiUrl = '/api/v1/chat', sessionId: initialSessionId } = options;

  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sessionId] = useState(() => initialSessionId || generateId());
  const abortControllerRef = useRef<AbortController | null>(null);

  const sendMessage = useCallback(async (content: string) => {
    if (!content.trim() || isLoading) return;

    // Add user message
    const userMessage: Message = {
      id: generateId(),
      role: 'user',
      content: content.trim(),
    };

    setMessages(prev => [...prev, userMessage]);
    setIsLoading(true);
    setError(null);

    // Create assistant message placeholder
    const assistantMessage: Message = {
      id: generateId(),
      role: 'assistant',
      content: '',
      toolCalls: [],
      isStreaming: true,
    };

    setMessages(prev => [...prev, assistantMessage]);

    // Abort previous request if any
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    abortControllerRef.current = new AbortController();

    try {
      const response = await fetch(apiUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          message: content.trim(),
          sessionId,
        }),
        signal: abortControllerRef.current.signal,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error('No response body');
      }

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          // Handle SSE format - slice after "data:"
          if (line.startsWith('data:')) {
            let data = line.slice(5); // Slice after "data:"

            // SSE format may have a space after "data:"
            // Only trim if followed by a protocol prefix (0:, 9:, a:, d:, e:)
            if (data.startsWith(' ') && /^[ ][0-9ade]:/.test(data)) {
              data = data.slice(1);
            }

            if (data === '[DONE]' || data === '' || data === ' ') continue;

            // Parse data protocol or text
            parseStreamData(data, assistantMessage.id, setMessages);
          }
        }
      }

      // Mark streaming complete
      setMessages(prev =>
        prev.map(msg =>
          msg.id === assistantMessage.id
            ? { ...msg, isStreaming: false }
            : msg
        )
      );
    } catch (err) {
      if (err instanceof Error && err.name === 'AbortError') {
        return;
      }
      const errorMessage = err instanceof Error ? err.message : 'Unknown error';
      setError(errorMessage);
      setMessages(prev =>
        prev.map(msg =>
          msg.id === assistantMessage.id
            ? { ...msg, content: `Error: ${errorMessage}`, isStreaming: false }
            : msg
        )
      );
    } finally {
      setIsLoading(false);
    }
  }, [apiUrl, sessionId, isLoading]);

  const clearMessages = useCallback(() => {
    setMessages([]);
    setError(null);
  }, []);

  const stopGeneration = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      setIsLoading(false);
    }
  }, []);

  return {
    messages,
    isLoading,
    error,
    sessionId,
    sendMessage,
    clearMessages,
    stopGeneration,
  };
}

function generateId(): string {
  return Math.random().toString(36).substring(2, 15);
}

function parseStreamData(
  data: string,
  messageId: string,
  setMessages: React.Dispatch<React.SetStateAction<Message[]>>
) {
  // Handle simple text data - check for protocol prefixes
  const protocolPrefixes = ['0:', '9:', 'a:', 'd:', 'e:'];
  const isProtocol = protocolPrefixes.some(p => data.startsWith(p));

  if (!isProtocol) {
    // Plain text chunk
    setMessages(prev =>
      prev.map(msg =>
        msg.id === messageId
          ? { ...msg, content: msg.content + data }
          : msg
      )
    );
    return;
  }

  // Handle data protocol
  const prefix = data.charAt(0);
  const payload = data.slice(2);

  try {
    switch (prefix) {
      case '0': {
        // Text content
        const text = JSON.parse(payload);
        setMessages(prev =>
          prev.map(msg =>
            msg.id === messageId
              ? { ...msg, content: msg.content + text }
              : msg
          )
        );
        break;
      }
      case '9': {
        // Tool call start
        const toolCall = JSON.parse(payload);
        setMessages(prev =>
          prev.map(msg =>
            msg.id === messageId
              ? {
                  ...msg,
                  toolCalls: [
                    ...(msg.toolCalls || []),
                    {
                      id: toolCall.toolCallId,
                      name: toolCall.toolName,
                      args: toolCall.args,
                      status: 'pending',
                    },
                  ],
                }
              : msg
          )
        );
        break;
      }
      case 'a': {
        // Tool call result
        const resultData = JSON.parse(payload);
        // result.result can be a JSON object or a string - stringify if object
        const resultStr = typeof resultData.result === 'object'
          ? JSON.stringify(resultData.result)
          : resultData.result;
        setMessages(prev =>
          prev.map(msg =>
            msg.id === messageId
              ? {
                  ...msg,
                  toolCalls: msg.toolCalls?.map(tc =>
                    tc.id === resultData.toolCallId
                      ? { ...tc, result: resultStr, status: 'complete' as const }
                      : tc
                  ),
                }
              : msg
          )
        );
        break;
      }
      case 'd': {
        // Finish
        // Mark message as complete
        break;
      }
      case 'e': {
        // Error
        const errorData = JSON.parse(payload);
        setMessages(prev =>
          prev.map(msg =>
            msg.id === messageId
              ? { ...msg, content: msg.content + `\n\nError: ${errorData.error}`, isStreaming: false }
              : msg
          )
        );
        break;
      }
    }
  } catch {
    // If parsing fails, treat as plain text
    setMessages(prev =>
      prev.map(msg =>
        msg.id === messageId
          ? { ...msg, content: msg.content + data }
          : msg
      )
    );
  }
}
