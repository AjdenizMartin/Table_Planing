import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#f5f7f2",
          100: "#e7eddc",
          200: "#d1ddb9",
          300: "#b1c98a",
          400: "#92b05f",
          500: "#789545",
          600: "#5d7434",
          700: "#49592a",
          800: "#3c4825",
          900: "#333d22"
        }
      },
      keyframes: {
        "slide-up": {
          "0%": { transform: "translateY(100%)", opacity: "0" },
          "100%": { transform: "translateY(0)", opacity: "1" }
        }
      },
      animation: {
        "slide-up": "slide-up 0.3s ease-out"
      }
    },
  },
  plugins: [],
};

export default config;

