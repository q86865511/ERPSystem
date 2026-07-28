/** Type CSS Module imports (`import classes from './x.module.css'`) as a string map. */
declare module '*.module.css' {
  const classes: { readonly [key: string]: string };
  export default classes;
}

/** TS 6 起 side-effect 匯入(`import './x.css'`)也要求可解析的模組宣告(TS2882);Vite 於建置期處理實際 CSS。 */
declare module '*.css';
