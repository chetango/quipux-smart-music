<template>
  <RouterView />
</template>

<style>
*, *::before, *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: #f4f6f9;
  color: #1a1a2e;
  line-height: 1.5;
}

a {
  color: inherit;
  text-decoration: none;
}

button {
  cursor: pointer;
  font-family: inherit;
  font-size: 0.875rem;
}

input, select, textarea {
  font-family: inherit;
  font-size: 0.875rem;
}
</style>
