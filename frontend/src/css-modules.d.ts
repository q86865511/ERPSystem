/** Type CSS Module imports (`import classes from './x.module.css'`) as a string map. */
declare module '*.module.css' {
  const classes: { readonly [key: string]: string };
  export default classes;
}
