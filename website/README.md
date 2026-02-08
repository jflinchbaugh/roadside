# Roadside Web - ClojureScript + Helix Single Page App

A single page application built with ClojureScript and Helix (React wrapper).

## What's Built

- ClojureScript app with Helix (React wrapper)
- Counter component with increment/decrement
- Development server with hot reload

## Getting Started

### Install Dependencies

```bash
npm install
```

### Run Development Server

```bash
npx shadow-cljs watch frontend
```

If the server was already running, stop it with Ctrl+C and restart it.

### View the App

Visit: http://localhost:3000/index.html

The development server includes hot reload,
so changes to your ClojureScript code
will be automatically reflected in the browser.

### Run the Automated Tests in the Browser

```bash
npx shadow-cljs watch frontend
```

Visit http://localhost:3021/
to see the execution and results of the tests.

Run both simultaneously with:
```bash
npx shadow-cljs watch frontend test-browser
```

### Tests Also Run in Node

```bash
npx shadow-cljs compile test
```

### Build for Release

```bash
npx shadow-cljs release frontend
```
