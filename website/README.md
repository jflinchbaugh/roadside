# Roadside Web - ClojureScript + Helix Single Page App

A single page application built with ClojureScript and Helix (React wrapper).

## What's Built

- ClojureScript app with Helix (React wrapper)
- Counter component with increment/decrement
- Todo app with add/toggle/delete functionality  
- Development server with hot reload

## Technologies

- ClojureScript 1.11.132
- Clojure 1.12.1
- Helix 0.2.1
- React 18
- Shadow-cljs for build tooling

## Getting Started

### Install Dependencies

```bash
npm install
```

### Run Development Server

```bash
npx shadow-cljs watch app
```

If the server was already running, stop it with Ctrl+C and restart it.

### View the App

Visit: http://localhost:8080

The development server includes hot reload, so changes to your ClojureScript code will be automatically reflected in the browser.

## Project Structure

```
├── src/
│   └── app/
│       └── core.cljs          # Main application code
├── resources/
│   └── public/
│       ├── index.html         # HTML entry point
│       └── js/                # Compiled JavaScript output
├── deps.edn                   # Clojure dependencies
├── shadow-cljs.edn           # Build configuration
└── package.json              # Node.js dependencies
```