import eslint from "@eslint/js";
import eslintConfigPrettier from "eslint-config-prettier/flat";
import html from "eslint-plugin-html";
import globals from "globals";
import tseslint from "typescript-eslint";

const unusedVars = [
  "error",
  {
    argsIgnorePattern: "^_",
    caughtErrorsIgnorePattern: "^_",
    varsIgnorePattern: "^_",
  },
];

const browserScriptRules = {
  // Empty catches are intentional when browser storage is unavailable.
  "no-empty": ["error", { allowEmptyCatch: true }],
  "no-unused-vars": unusedVars,
};

const scriptGlobals = {
  ...globals.browser,
  IS_DESKTOP: "readonly",
  applyDownloadAvailability: "readonly",
  downloadPlatform: "readonly",
};

export default [
  {
    ignores: [
      "**/node_modules/**",
      "**/build/**",
      "**/dist/**",
      "**/.gradle/**",
      "package-lock.json",
      "**/package-lock.json",
    ],
  },
  {
    files: [
      "client/**/*.{js,cjs,mjs}",
      "formal/**/*.{js,cjs,mjs}",
      "eslint.config.mjs",
      "prettier.config.mjs",
      "scripts/**/*.mjs",
    ],
    ...eslint.configs.recommended,
    rules: {
      ...eslint.configs.recommended.rules,
      ...browserScriptRules,
    },
  },
  {
    files: ["client/**/*.js"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "script",
      globals: globals.browser,
    },
  },
  {
    files: ["client/downloads.js"],
    rules: {
      // Called from inline scripts in client/index.html, not from this file.
      "no-unused-vars": [
        "error",
        {
          argsIgnorePattern: "^_",
          caughtErrorsIgnorePattern: "^_",
          varsIgnorePattern: "^(_|applyDownloadAvailability)$",
        },
      ],
    },
  },
  {
    files: ["client/**/*.cjs", "formal/**/*.cjs"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "commonjs",
      globals: globals.node,
    },
  },
  {
    files: [
      "formal/**/*.js",
      "formal/**/*.mjs",
      "eslint.config.mjs",
      "prettier.config.mjs",
      "scripts/**/*.mjs",
    ],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      globals: globals.node,
    },
  },
  {
    files: ["client/**/*.html", "toss-client/**/*.html"],
    plugins: { html },
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "script",
      globals: scriptGlobals,
    },
    rules: {
      ...eslint.configs.recommended.rules,
      ...browserScriptRules,
    },
  },
  {
    files: ["toss-client/**/*.ts"],
    rules: eslint.configs.recommended.rules,
  },
  ...tseslint.configs.recommended.map((config) => ({
    ...config,
    files: ["toss-client/**/*.ts"],
  })),
  {
    files: ["toss-client/**/*.ts"],
    rules: {
      "@typescript-eslint/no-unused-vars": unusedVars,
    },
  },
  eslintConfigPrettier,
];
