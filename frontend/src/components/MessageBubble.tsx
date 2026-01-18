import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { User, Bot } from 'lucide-react';
import { Message } from '../hooks/useDataLensChat';
import { ToolCallCard } from './ToolCallCard';

interface MessageBubbleProps {
  message: Message;
}

export function MessageBubble({ message }: MessageBubbleProps) {
  const isUser = message.role === 'user';

  return (
    <div className={`flex gap-3 ${isUser ? 'justify-end' : 'justify-start'}`}>
      {!isUser && (
        <div className="flex-shrink-0">
          <div className="w-8 h-8 bg-primary-100 dark:bg-primary-900 rounded-lg flex items-center justify-center">
            <Bot className="w-5 h-5 text-primary-600 dark:text-primary-400" />
          </div>
        </div>
      )}

      <div
        className={`max-w-[80%] ${
          isUser
            ? 'bg-primary-600 text-white rounded-2xl rounded-tr-md'
            : 'bg-white dark:bg-neutral-900 border border-gray-200 dark:border-neutral-800 rounded-2xl rounded-tl-md'
        } px-4 py-3 shadow-sm`}
      >
        {/* Tool Calls */}
        {message.toolCalls && message.toolCalls.length > 0 && (
          <div className="mb-3 space-y-2">
            {message.toolCalls.map((toolCall) => (
              <ToolCallCard key={toolCall.id} toolCall={toolCall} />
            ))}
          </div>
        )}

        {/* Message Content */}
        {message.content && (
          <div
            className={`prose prose-sm max-w-none break-words overflow-hidden ${
              isUser
                ? 'prose-invert'
                : 'prose-gray dark:prose-invert'
            }`}
            style={{ wordBreak: 'break-word', overflowWrap: 'break-word' }}
          >
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                // Custom rendering for code blocks
                pre: ({ children }) => (
                  <pre className="bg-gray-900 dark:bg-black text-gray-100 rounded-lg p-3 overflow-x-auto text-xs border dark:border-neutral-700">
                    {children}
                  </pre>
                ),
                code: ({ className, children, ...props }) => {
                  const isInline = !className;
                  if (isInline) {
                    return (
                      <code
                        className={`${
                          isUser
                            ? 'bg-primary-500 text-white'
                            : 'bg-gray-100 dark:bg-neutral-800 text-gray-800 dark:text-neutral-200'
                        } px-1 py-0.5 rounded text-xs`}
                        {...props}
                      >
                        {children}
                      </code>
                    );
                  }
                  return <code {...props}>{children}</code>;
                },
                // Tables
                table: ({ children }) => (
                  <div className="overflow-x-auto my-2">
                    <table className="min-w-full border-collapse text-xs">
                      {children}
                    </table>
                  </div>
                ),
                th: ({ children }) => (
                  <th className="border border-gray-300 dark:border-neutral-700 px-2 py-1 bg-gray-100 dark:bg-neutral-800 font-semibold text-left text-gray-800 dark:text-white">
                    {children}
                  </th>
                ),
                td: ({ children }) => (
                  <td className="border border-gray-300 dark:border-neutral-700 px-2 py-1 text-gray-700 dark:text-neutral-300">{children}</td>
                ),
              }}
            >
              {message.content}
            </ReactMarkdown>
          </div>
        )}

        {/* Streaming indicator */}
        {message.isStreaming && !message.content && (
          <div className="flex items-center gap-2 text-gray-400 dark:text-neutral-500">
            <div className="flex gap-1">
              <span className="w-2 h-2 bg-gray-400 dark:bg-neutral-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }}></span>
              <span className="w-2 h-2 bg-gray-400 dark:bg-neutral-500 rounded-full animate-bounce" style={{ animationDelay: '150ms' }}></span>
              <span className="w-2 h-2 bg-gray-400 dark:bg-neutral-500 rounded-full animate-bounce" style={{ animationDelay: '300ms' }}></span>
            </div>
            <span className="text-sm">Thinking...</span>
          </div>
        )}

        {message.isStreaming && message.content && (
          <span className="streaming-cursor"></span>
        )}
      </div>

      {isUser && (
        <div className="flex-shrink-0">
          <div className="w-8 h-8 bg-gray-200 dark:bg-neutral-800 rounded-lg flex items-center justify-center">
            <User className="w-5 h-5 text-gray-600 dark:text-neutral-300" />
          </div>
        </div>
      )}
    </div>
  );
}
