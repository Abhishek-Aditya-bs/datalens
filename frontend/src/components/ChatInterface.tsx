import { useState, useRef, useEffect } from 'react';
import { Send, Loader2, Database, Trash2 } from 'lucide-react';
import { useDataLensChat, Message } from '../hooks/useDataLensChat';
import { MessageBubble } from './MessageBubble';

const EXAMPLE_QUERIES = [
  'List all available tables',
  'Show me the schema for USERS table',
  'Select top 5 records from ORDERS',
  'What is the current connection status?',
];

export function ChatInterface() {
  const [input, setInput] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const {
    messages,
    isLoading,
    error,
    sendMessage,
    clearMessages,
    stopGeneration,
  } = useDataLensChat({
    apiUrl: '/api/v1/chat/stream', // Use stream endpoint to get tool call events
  });

  // Auto-scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Auto-focus input
  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (input.trim() && !isLoading) {
      sendMessage(input);
      setInput('');
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  const handleExampleClick = (query: string) => {
    setInput(query);
    inputRef.current?.focus();
  };

  return (
    <div className="flex flex-col h-full max-w-6xl mx-auto">
      {/* Messages Area */}
      <div className="flex-1 overflow-y-auto px-4 py-6">
        {messages.length === 0 ? (
          <WelcomeScreen onExampleClick={handleExampleClick} />
        ) : (
          <div className="space-y-6">
            {messages.map((message) => (
              <MessageBubble key={message.id} message={message} />
            ))}
            <div ref={messagesEndRef} />
          </div>
        )}
      </div>

      {/* Error Display */}
      {error && (
        <div className="mx-4 mb-4 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-red-700 dark:text-red-300 text-sm">
          {error}
        </div>
      )}

      {/* Input Area */}
      <div className="border-t border-gray-200 dark:border-neutral-800 bg-white dark:bg-black px-4 py-4 transition-colors">
        <form onSubmit={handleSubmit} className="flex items-end gap-3">
          <div className="flex-1 relative">
            <textarea
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Ask about your data..."
              rows={1}
              className="w-full resize-none rounded-xl border border-gray-300 dark:border-neutral-700 bg-white dark:bg-neutral-900 text-gray-900 dark:text-white px-4 py-3 pr-12 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent text-sm placeholder-gray-400 dark:placeholder-neutral-500"
              style={{ minHeight: '48px', maxHeight: '200px' }}
              disabled={isLoading}
            />
          </div>

          <div className="flex items-center gap-2">
            {messages.length > 0 && (
              <button
                type="button"
                onClick={clearMessages}
                className="p-3 text-gray-400 hover:text-gray-600 dark:hover:text-neutral-300 hover:bg-gray-100 dark:hover:bg-neutral-800 rounded-xl transition-colors"
                title="Clear chat"
              >
                <Trash2 className="w-5 h-5" />
              </button>
            )}

            {isLoading ? (
              <button
                type="button"
                onClick={stopGeneration}
                className="p-3 bg-red-500 text-white rounded-xl hover:bg-red-600 transition-colors"
                title="Stop generation"
              >
                <Loader2 className="w-5 h-5 animate-spin" />
              </button>
            ) : (
              <button
                type="submit"
                disabled={!input.trim()}
                className="p-3 bg-primary-600 text-white rounded-xl hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                title="Send message"
              >
                <Send className="w-5 h-5" />
              </button>
            )}
          </div>
        </form>

        <div className="mt-2 text-xs text-gray-400 dark:text-neutral-500 text-center">
          Press Enter to send, Shift+Enter for new line
        </div>
      </div>
    </div>
  );
}

function WelcomeScreen({ onExampleClick }: { onExampleClick: (query: string) => void }) {
  return (
    <div className="flex flex-col items-center justify-center h-full text-center px-4">
      <div className="w-16 h-16 bg-primary-100 dark:bg-primary-900/50 rounded-2xl flex items-center justify-center mb-6">
        <Database className="w-8 h-8 text-primary-600 dark:text-primary-400" />
      </div>

      <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
        Welcome to DataLens
      </h2>
      <p className="text-gray-500 dark:text-neutral-400 mb-8 max-w-md">
        Your AI-powered database assistant. Ask questions about your data,
        explore tables, and run queries using natural language.
      </p>

      <div className="w-full max-w-lg">
        <h3 className="text-sm font-medium text-gray-700 dark:text-neutral-300 mb-3">
          Try these example queries:
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          {EXAMPLE_QUERIES.map((query, index) => (
            <button
              key={index}
              onClick={() => onExampleClick(query)}
              className="text-left px-4 py-3 bg-white dark:bg-neutral-900 border border-gray-200 dark:border-neutral-800 rounded-lg hover:border-primary-300 dark:hover:border-primary-700 hover:bg-primary-50 dark:hover:bg-primary-900/20 transition-colors text-sm text-gray-700 dark:text-neutral-300"
            >
              {query}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
