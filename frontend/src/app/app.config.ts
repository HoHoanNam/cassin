import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // withComponentInputBinding: tham số trên URL (vd. :id trong /decks/:id)
    // được truyền thẳng vào input() cùng tên của component trang.
    provideRouter(routes, withComponentInputBinding()),
  ],
};
