import { useState } from 'react';
import { ChevronDown, ChevronRight, Database, Link, List, Table, CheckCircle, Loader2, AlertCircle, Code, Play } from 'lucide-react';
import { ToolCall } from '../hooks/useDataLensChat';

interface ToolCallCardProps {
  toolCall: ToolCall;
}

const TOOL_ICONS: Record<string, React.ReactNode> = {
  executeQuery: <Database className="w-4 h-4" />,
  connectToEnvironment: <Link className="w-4 h-4" />,
  getCurrentStatus: <CheckCircle className="w-4 h-4" />,
  getTableSchema: <Table className="w-4 h-4" />,
  listTables: <List className="w-4 h-4" />,
};

const TOOL_LABELS: Record<string, string> = {
  executeQuery: 'Execute Query',
  connectToEnvironment: 'Connect to Environment',
  getCurrentStatus: 'Get Connection Status',
  getTableSchema: 'Get Table Schema',
  listTables: 'List Tables',
};

const TOOL_DESCRIPTIONS: Record<string, string> = {
  executeQuery: 'Running SQL query against the database',
  connectToEnvironment: 'Switching database environment',
  getCurrentStatus: 'Checking connection health',
  getTableSchema: 'Fetching table structure',
  listTables: 'Listing available tables',
};

export function ToolCallCard({ toolCall }: ToolCallCardProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  const icon = TOOL_ICONS[toolCall.name] || <Code className="w-4 h-4" />;
  const label = TOOL_LABELS[toolCall.name] || toolCall.name;
  const description = TOOL_DESCRIPTIONS[toolCall.name] || 'Executing tool';

  const statusConfig = {
    pending: {
      icon: <Loader2 className="w-4 h-4 animate-spin" />,
      bgColor: 'bg-blue-50 dark:bg-blue-900/30',
      borderColor: 'border-blue-200 dark:border-blue-800',
      iconBgColor: 'bg-blue-100 dark:bg-blue-900',
      iconColor: 'text-blue-600 dark:text-blue-400',
      statusText: 'Running...',
      statusColor: 'text-blue-600 dark:text-blue-400',
    },
    complete: {
      icon: <CheckCircle className="w-4 h-4" />,
      bgColor: 'bg-green-50 dark:bg-green-900/30',
      borderColor: 'border-green-200 dark:border-green-800',
      iconBgColor: 'bg-green-100 dark:bg-green-900',
      iconColor: 'text-green-600 dark:text-green-400',
      statusText: 'Completed',
      statusColor: 'text-green-600 dark:text-green-400',
    },
    error: {
      icon: <AlertCircle className="w-4 h-4" />,
      bgColor: 'bg-red-50 dark:bg-red-900/30',
      borderColor: 'border-red-200 dark:border-red-800',
      iconBgColor: 'bg-red-100 dark:bg-red-900',
      iconColor: 'text-red-600 dark:text-red-400',
      statusText: 'Error',
      statusColor: 'text-red-600 dark:text-red-400',
    },
  }[toolCall.status];

  const parsedResult = toolCall.result ? tryParseJson(toolCall.result) : null;

  return (
    <div className={`${statusConfig.bgColor} border ${statusConfig.borderColor} rounded-lg overflow-hidden text-sm`}>
      {/* Header - Always visible */}
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className="w-full flex items-center gap-3 px-4 py-3 hover:bg-white/50 dark:hover:bg-white/10 transition-colors"
      >
        {/* Expand/Collapse Icon */}
        <div className="text-gray-400 dark:text-neutral-500">
          {isExpanded ? (
            <ChevronDown className="w-4 h-4" />
          ) : (
            <ChevronRight className="w-4 h-4" />
          )}
        </div>

        {/* Tool Icon */}
        <div className={`w-8 h-8 ${statusConfig.iconBgColor} rounded-lg flex items-center justify-center ${statusConfig.iconColor}`}>
          {icon}
        </div>

        {/* Tool Info */}
        <div className="flex-1 text-left">
          <div className="font-semibold text-gray-800 dark:text-neutral-200">{label}</div>
          <div className="text-xs text-gray-500 dark:text-neutral-400">{description}</div>
        </div>

        {/* Status Badge */}
        <div className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full ${statusConfig.iconBgColor} ${statusConfig.statusColor}`}>
          {statusConfig.icon}
          <span className="text-xs font-medium">{statusConfig.statusText}</span>
        </div>
      </button>

      {/* Expanded Content */}
      {isExpanded && (
        <div className="border-t border-gray-200 dark:border-neutral-700 bg-white dark:bg-neutral-900 p-4 space-y-4">
          {/* Request Section */}
          {Object.keys(toolCall.args).length > 0 && (
            <div>
              <div className="flex items-center gap-2 mb-2">
                <Play className="w-3.5 h-3.5 text-gray-400 dark:text-neutral-500" />
                <span className="text-xs font-semibold text-gray-500 dark:text-neutral-400 uppercase tracking-wider">Request</span>
              </div>
              <div className="bg-gray-900 dark:bg-black rounded-lg p-3 overflow-x-auto border dark:border-neutral-700">
                <pre className="text-gray-100 text-xs font-mono whitespace-pre-wrap break-all">
                  {JSON.stringify(toolCall.args, null, 2)}
                </pre>
              </div>
            </div>
          )}

          {/* Response Section */}
          {toolCall.result && (
            <div>
              <div className="flex items-center gap-2 mb-2">
                <CheckCircle className="w-3.5 h-3.5 text-gray-400 dark:text-neutral-500" />
                <span className="text-xs font-semibold text-gray-500 dark:text-neutral-400 uppercase tracking-wider">Response</span>
              </div>
              {parsedResult && typeof parsedResult === 'object' ? (
                <QueryResultTable data={parsedResult} />
              ) : (
                <div className="bg-gray-900 dark:bg-black rounded-lg p-3 overflow-x-auto max-h-64 overflow-y-auto border dark:border-neutral-700">
                  <pre className="text-gray-100 text-xs font-mono whitespace-pre-wrap break-all">
                    {typeof parsedResult === 'string'
                      ? parsedResult
                      : JSON.stringify(parsedResult, null, 2)}
                  </pre>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function QueryResultTable({ data }: { data: Record<string, unknown> }) {
  // Handle query results with columnNames and rows
  if (data.columnNames && Array.isArray(data.columnNames) && data.rows && Array.isArray(data.rows)) {
    const columns = data.columnNames as string[];
    const rows = data.rows as unknown[][];

    return (
      <div className="bg-white dark:bg-neutral-800 border border-gray-200 dark:border-neutral-700 rounded-lg overflow-hidden">
        <div className="px-3 py-2 bg-gray-50 dark:bg-neutral-900 border-b border-gray-200 dark:border-neutral-700 flex items-center justify-between">
          <span className="text-xs font-medium text-gray-600 dark:text-neutral-400">
            Query Results
          </span>
          <span className="text-xs text-gray-500 dark:text-neutral-500">
            {data.rowCount} row{(data.rowCount as number) !== 1 ? 's' : ''} • {data.executionTimeMs}ms
          </span>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full w-max divide-y divide-gray-200 dark:divide-neutral-700">
            <thead className="bg-gray-50 dark:bg-neutral-900">
              <tr>
                {columns.map((col, i) => (
                  <th
                    key={i}
                    className="px-3 py-2 text-left text-xs font-semibold text-gray-600 dark:text-neutral-400 uppercase tracking-wider"
                  >
                    {col}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="bg-white dark:bg-neutral-800 divide-y divide-gray-200 dark:divide-neutral-700">
              {rows.slice(0, 20).map((row, rowIndex) => (
                <tr key={rowIndex} className="hover:bg-gray-50 dark:hover:bg-neutral-800">
                  {(row as unknown[]).map((cell, cellIndex) => (
                    <td
                      key={cellIndex}
                      className="px-3 py-2 text-xs text-gray-700 dark:text-neutral-300 whitespace-nowrap"
                    >
                      {cell === null ? (
                        <span className="text-gray-400 dark:text-neutral-500 italic">null</span>
                      ) : (
                        String(cell)
                      )}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {rows.length > 20 && (
          <div className="px-3 py-2 bg-gray-50 dark:bg-neutral-900 border-t border-gray-200 dark:border-neutral-700 text-xs text-gray-500 dark:text-neutral-400">
            Showing first 20 of {rows.length} rows
          </div>
        )}
      </div>
    );
  }

  // Handle table list
  if (data.tables && Array.isArray(data.tables)) {
    return (
      <div className="bg-white dark:bg-neutral-800 border border-gray-200 dark:border-neutral-700 rounded-lg overflow-hidden">
        <div className="px-3 py-2 bg-gray-50 dark:bg-neutral-900 border-b border-gray-200 dark:border-neutral-700 flex items-center justify-between">
          <span className="text-xs font-medium text-gray-600 dark:text-neutral-400">
            Tables in {data.schema as string}
          </span>
          <span className="text-xs text-gray-500 dark:text-neutral-500">
            {data.tableCount} table{(data.tableCount as number) !== 1 ? 's' : ''}
          </span>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full w-max divide-y divide-gray-200 dark:divide-neutral-700">
            <thead className="bg-gray-50 dark:bg-neutral-900">
              <tr>
                <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600 dark:text-neutral-400 uppercase tracking-wider">
                  Table Name
                </th>
                <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600 dark:text-neutral-400 uppercase tracking-wider">
                  Type
                </th>
                <th className="px-3 py-2 text-right text-xs font-semibold text-gray-600 dark:text-neutral-400 uppercase tracking-wider">
                  Rows
                </th>
              </tr>
            </thead>
            <tbody className="bg-white dark:bg-neutral-800 divide-y divide-gray-200 dark:divide-neutral-700">
              {(data.tables as Array<{ tableName: string; tableType: string; rowCount: number }>).map(
                (table, i) => (
                  <tr key={i} className="hover:bg-gray-50 dark:hover:bg-neutral-800">
                    <td className="px-3 py-2 text-xs font-mono text-gray-700 dark:text-neutral-300">
                      {table.tableName}
                    </td>
                    <td className="px-3 py-2 text-xs text-gray-600 dark:text-neutral-400">
                      {table.tableType}
                    </td>
                    <td className="px-3 py-2 text-xs text-gray-600 dark:text-neutral-400 text-right">
                      {table.rowCount?.toLocaleString() || '—'}
                    </td>
                  </tr>
                )
              )}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  // Handle table schema
  if (data.columns && Array.isArray(data.columns)) {
    return (
      <div className="bg-white dark:bg-neutral-800 border border-gray-200 dark:border-neutral-700 rounded-lg overflow-hidden">
        <div className="px-3 py-2 bg-gray-50 dark:bg-neutral-900 border-b border-gray-200 dark:border-neutral-700">
          <span className="text-xs font-medium text-gray-600 dark:text-neutral-400">
            Schema: {data.schemaName as string}.{data.tableName as string}
          </span>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full w-max divide-y divide-gray-200 dark:divide-neutral-700">
            <thead className="bg-gray-50 dark:bg-neutral-900">
              <tr>
                <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600 dark:text-neutral-400 uppercase tracking-wider">
                  Column
                </th>
                <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600 dark:text-neutral-400 uppercase tracking-wider">
                  Type
                </th>
                <th className="px-3 py-2 text-center text-xs font-semibold text-gray-600 dark:text-neutral-400 uppercase tracking-wider">
                  Size
                </th>
                <th className="px-3 py-2 text-center text-xs font-semibold text-gray-600 dark:text-neutral-400 uppercase tracking-wider">
                  Nullable
                </th>
              </tr>
            </thead>
            <tbody className="bg-white dark:bg-neutral-800 divide-y divide-gray-200 dark:divide-neutral-700">
              {(
                data.columns as Array<{
                  name: string;
                  dataType: string;
                  size: number;
                  nullable: boolean;
                }>
              ).map((col, i) => (
                <tr key={i} className="hover:bg-gray-50 dark:hover:bg-neutral-800">
                  <td className="px-3 py-2 text-xs font-mono text-gray-700 dark:text-neutral-300">
                    {col.name}
                  </td>
                  <td className="px-3 py-2 text-xs text-gray-600 dark:text-neutral-400">
                    {col.dataType}
                  </td>
                  <td className="px-3 py-2 text-xs text-gray-600 dark:text-neutral-400 text-center">
                    {col.size}
                  </td>
                  <td className="px-3 py-2 text-xs text-center">
                    {col.nullable ? (
                      <span className="text-green-600 dark:text-green-400">Yes</span>
                    ) : (
                      <span className="text-red-600 dark:text-red-400">No</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data.primaryKeys && (data.primaryKeys as string[]).length > 0 && (
          <div className="px-3 py-2 bg-gray-50 dark:bg-neutral-900 border-t border-gray-200 dark:border-neutral-700 text-xs text-gray-600 dark:text-neutral-400">
            <span className="font-medium">Primary Key:</span> {(data.primaryKeys as string[]).join(', ')}
          </div>
        )}
      </div>
    );
  }

  // Fallback to JSON display
  return (
    <div className="bg-gray-900 dark:bg-black rounded-lg p-3 overflow-x-auto max-h-64 overflow-y-auto border dark:border-neutral-700">
      <pre className="text-gray-100 text-xs font-mono whitespace-pre-wrap break-all">
        {JSON.stringify(data, null, 2)}
      </pre>
    </div>
  );
}

function tryParseJson(str: string): unknown {
  try {
    return JSON.parse(str);
  } catch {
    return str;
  }
}
