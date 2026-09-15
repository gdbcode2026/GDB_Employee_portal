import { globalIgnores } from "eslint/config";
import typescriptParser from "@typescript-eslint/parser";

// Next 15's legacy config is not ESLint 9-flat-config compatible; this small
// foundation keeps linting active until the frontend standard is finalized.
export default [
  { files: ["**/*.{js,mjs,ts,tsx}"], languageOptions: { parser: typescriptParser, parserOptions: { ecmaFeatures: { jsx: true } } }, rules: {} },
  globalIgnores([".next/**", "node_modules/**"])
];
