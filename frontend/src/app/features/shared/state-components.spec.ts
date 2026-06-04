import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { EmptyStateComponent } from './empty-state.component';
import { LoadingStateComponent } from './loading-state.component';
import { ErrorStateComponent } from './error-state.component';

/**
 * Feature 007-ui-ux-improvements - shared state components (FR-013/014/015).
 * Verifies the ARIA roles that make each state perceivable to assistive technology.
 */
describe('Shared state components', () => {
  it('empty-state renders heading + message and projects CTA content', () => {
    @Component({
      standalone: true,
      imports: [EmptyStateComponent],
      template: `<ig-empty-state heading="None yet" message="Try again">
                   <a class="cta">Go</a>
                 </ig-empty-state>`,
    })
    class Host {}

    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.ig-empty h2')?.textContent).toContain('None yet');
    expect(el.querySelector('.ig-empty p')?.textContent).toContain('Try again');
    expect(el.querySelector('.ig-empty__actions .cta')).toBeTruthy();
  });

  it('loading-state exposes role="status" with the label', () => {
    const fixture = TestBed.createComponent(LoadingStateComponent);
    fixture.componentRef.setInput('label', 'Loading...');
    fixture.detectChanges();
    const status: HTMLElement | null = fixture.nativeElement.querySelector('[role="status"]');
    expect(status).toBeTruthy();
    expect(status!.textContent).toContain('Loading...');
  });

  it('error-state exposes role="alert" with the message', () => {
    const fixture = TestBed.createComponent(ErrorStateComponent);
    fixture.componentRef.setInput('message', 'Something failed');
    fixture.detectChanges();
    const alert: HTMLElement | null = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert).toBeTruthy();
    expect(alert!.textContent).toContain('Something failed');
  });
});
