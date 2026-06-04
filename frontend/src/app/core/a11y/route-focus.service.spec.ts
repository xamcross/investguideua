import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { RouteFocusService } from './route-focus.service';

/**
 * Feature 007-ui-ux-improvements - C-A11Y-2: after each SPA navigation focus moves to the main
 * landmark, and the service is a no-op during server render.
 */
describe('RouteFocusService', () => {
  let main: HTMLElement;

  function configure(events: Subject<unknown>, platform: 'browser' | 'server') {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        RouteFocusService,
        { provide: Router, useValue: { events } },
        { provide: PLATFORM_ID, useValue: platform },
      ],
    });
    return TestBed.inject(RouteFocusService);
  }

  beforeEach(() => {
    main = document.createElement('main');
    main.id = 'main-content';
    main.setAttribute('tabindex', '-1');
    document.body.appendChild(main);
  });

  afterEach(() => main.remove());

  it('focuses #main-content on NavigationEnd in the browser', () => {
    const events = new Subject<unknown>();
    const svc = configure(events, 'browser');
    svc.init();

    events.next(new NavigationEnd(1, '/a', '/a'));

    expect(document.activeElement).toBe(main);
  });

  it('does nothing (and does not throw) on the server platform', () => {
    const events = new Subject<unknown>();
    const svc = configure(events, 'server');

    expect(() => svc.init()).not.toThrow();
    events.next(new NavigationEnd(1, '/a', '/a'));
    expect(document.activeElement).not.toBe(main);
  });
});
