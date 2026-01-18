/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#eff6ff',
          100: '#dbeafe',
          200: '#bfdbfe',
          300: '#93c5fd',
          400: '#60a5fa',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
          800: '#1e40af',
          900: '#1e3a8a',
        },
      },
      typography: {
        DEFAULT: {
          css: {
            maxWidth: 'none',
          },
        },
        invert: {
          css: {
            '--tw-prose-body': '#e5e5e5',
            '--tw-prose-headings': '#ffffff',
            '--tw-prose-lead': '#d4d4d4',
            '--tw-prose-links': '#60a5fa',
            '--tw-prose-bold': '#ffffff',
            '--tw-prose-counters': '#a3a3a3',
            '--tw-prose-bullets': '#737373',
            '--tw-prose-hr': '#404040',
            '--tw-prose-quotes': '#d4d4d4',
            '--tw-prose-quote-borders': '#404040',
            '--tw-prose-captions': '#a3a3a3',
            '--tw-prose-code': '#ffffff',
            '--tw-prose-pre-code': '#e5e5e5',
            '--tw-prose-pre-bg': '#0a0a0a',
            '--tw-prose-th-borders': '#404040',
            '--tw-prose-td-borders': '#333333',
          },
        },
      },
    },
  },
  plugins: [
    require('@tailwindcss/typography'),
  ],
}
