import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Commit } from './commit';

describe('Commit', () => {
  let component: Commit;
  let fixture: ComponentFixture<Commit>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Commit],
    }).compileComponents();

    fixture = TestBed.createComponent(Commit);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
