# Roadside Stands Website

ClojureScript single page application for managing roadside fruit/vegetable stands with Helix (React wrapper).

## Development Commands
- Start dev server: `npx shadow-cljs watch app`
- Build for production: `npx shadow-cljs compile app`
- View app: http://localhost:8080

## Project Structure
```
├── src/app/core.cljs          # Main application code
├── resources/public/
│   ├── index.html             # HTML entry point
│   ├── styles.css            # All CSS styling
│   └── images/               # Static assets (apple logo)
├── shadow-cljs.edn           # Build configuration
└── package.json              # Node.js dependencies
```

## Architecture
- **Single page app** with modal overlay forms
- **State management**: React hooks (use-state, use-effect)
- **Components**: Functional components using Helix
- **Form handling**: Modal forms with keyboard shortcuts (ESC to close)
- **Data structure**: Stands contain name, location, products[], expiration date

## Code Style
- **Indentation**: 2 spaces
- **ClojureScript**: Use kebab-case for variable names
- **CSS**: Use kebab-case classes (.stand-item, .form-overlay)
- **Components**: Use defnc for Helix components
- **DOM**: Use helix.dom namespace (d/div, d/button, etc.)

## Form Behavior
- **Add mode**: Fresh form with default expiration (1 week ahead)
- **Edit mode**: Pre-populated with existing stand data
- **Products**: Tag-style input, press Enter to add, × to remove
- **Validation**: Name and location are required fields
- **Keyboard shortcuts**: ESC closes form, Enter adds products

## Styling Patterns
- **Buttons**: Consistent padding (6px 12px), border-radius 4px
- **Colors**: Green for primary actions, red for delete, blue for edit
- **Modal**: Dark overlay with centered white container
- **Tags**: Blue background (#e7f3ff) with rounded corners
- **Layout**: Flexbox for headers and button groups

## State Management
- `stands`: Array of stand objects
- `show-form`: Boolean for modal visibility  
- `editing-stand`: Currently edited stand object or nil
- `form-data`: Current form state with all fields
- `current-product`: Temporary input for adding products

## Dependencies
- ClojureScript + Helix for React integration
- Shadow-cljs for build tooling
- No external CSS frameworks (custom styles)