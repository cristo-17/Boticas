import { Component, Input, inject } from '@angular/core';
import { ControlValueAccessor, NgControl } from '@angular/forms';

export type FieldTipo = 'text' | 'password' | 'select';

export interface OpcionField {
  value: string;
  label: string;
}

let nextId = 0;

@Component({
  selector: 'app-field',
  templateUrl: './field.html',
})
export class FieldComponent implements ControlValueAccessor {
  private readonly ngControl = inject(NgControl, { optional: true, self: true });

  @Input() label = '';
  @Input() tipo: FieldTipo = 'text';
  @Input() opciones: OpcionField[] = [];
  @Input() placeholder = '';
  @Input() autocomplete: string | null = null;
  @Input() hint: string | null = null;
  @Input() errorMessage: string | null = null;
  @Input() inputId = `app-field-${nextId++}`;

  value: string = '';
  disabled = false;
  mostrarPassword = false;

  private onChange: (value: string) => void = () => {};
  private onTouchedFn: () => void = () => {};

  constructor() {
    if (this.ngControl) {
      this.ngControl.valueAccessor = this;
    }
  }

  get showError(): boolean {
    const control = this.ngControl?.control;
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  get tipoInputReal(): string {
    return this.tipo === 'password' && !this.mostrarPassword ? 'password' : 'text';
  }

  alternarMostrarPassword(): void {
    this.mostrarPassword = !this.mostrarPassword;
  }

  onInput(event: Event): void {
    this.value = (event.target as HTMLInputElement).value;
    this.onChange(this.value);
  }

  onSelectChange(event: Event): void {
    this.value = (event.target as HTMLSelectElement).value;
    this.onChange(this.value);
  }

  onBlur(): void {
    this.onTouchedFn();
  }

  writeValue(value: string): void {
    this.value = value ?? '';
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouchedFn = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }
}
