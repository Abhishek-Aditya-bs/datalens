import { ChatInterface } from './components/ChatInterface'
import { ThemeProvider, useTheme } from './contexts/ThemeContext'
import { Moon, Sun } from 'lucide-react'

function AppContent() {
  const { theme, toggleTheme } = useTheme();

  return (
    <div className="min-h-screen flex flex-col bg-gray-50 dark:bg-black transition-colors">
      {/* Header */}
      <header className="bg-white dark:bg-black border-b border-gray-200 dark:border-neutral-800 px-6 py-4 transition-colors">
        <div className="max-w-4xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-primary-600 rounded-lg flex items-center justify-center">
              <svg
                className="w-6 h-6 text-white"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4"
                />
              </svg>
            </div>
            <div>
              <h1 className="text-xl font-bold text-gray-900 dark:text-white">DataLens</h1>
              <p className="text-sm text-gray-500 dark:text-neutral-400">AI Database Assistant</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            {/* Theme Toggle */}
            <button
              onClick={toggleTheme}
              className="p-2.5 rounded-full bg-gray-100 dark:bg-neutral-800 hover:bg-gray-200 dark:hover:bg-neutral-700 transition-colors"
              title={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`}
            >
              {theme === 'light' ? (
                <Moon className="w-5 h-5 text-gray-600" />
              ) : (
                <Sun className="w-5 h-5 text-neutral-300" />
              )}
            </button>

            <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-green-100 dark:bg-green-900/50 text-green-800 dark:text-green-300">
              <span className="w-1.5 h-1.5 bg-green-500 rounded-full mr-1.5"></span>
              Connected
            </span>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 overflow-hidden">
        <ChatInterface />
      </main>

      {/* Footer */}
      <footer className="bg-white dark:bg-black border-t border-gray-200 dark:border-neutral-800 px-6 py-3 transition-colors">
        <div className="max-w-4xl mx-auto text-center text-xs text-gray-500 dark:text-neutral-500">
          DataLens v1.0.0 - AI-Powered Database Query Agent
        </div>
      </footer>
    </div>
  )
}

function App() {
  return (
    <ThemeProvider>
      <AppContent />
    </ThemeProvider>
  )
}

export default App
