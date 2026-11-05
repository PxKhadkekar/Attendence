import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { LoginPage } from '../pages/LoginPage';
import { AuthProvider } from '../context/AuthContext';

describe('LoginPage Component', () => {
  it('renders login form and preset button', () => {
    render(
      <AuthProvider>
        <BrowserRouter>
          <LoginPage />
        </BrowserRouter>
      </AuthProvider>
    );

    expect(screen.getByText('T&P Officer Portal')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('officer@college.edu')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('••••••••••••')).toBeInTheDocument();
    expect(screen.getByText(/Load Officer Admin Preset/)).toBeInTheDocument();
  });

  it('clicking preset button fills credentials', () => {
    render(
      <AuthProvider>
        <BrowserRouter>
          <LoginPage />
        </BrowserRouter>
      </AuthProvider>
    );

    const emailInput = screen.getByPlaceholderText('officer@college.edu') as HTMLInputElement;
    const passwordInput = screen.getByPlaceholderText('••••••••••••') as HTMLInputElement;

    // Clear fields
    fireEvent.change(emailInput, { target: { value: '' } });
    fireEvent.change(passwordInput, { target: { value: '' } });
    expect(emailInput.value).toBe('');

    // Click preset
    const presetBtn = screen.getByText(/Load Officer Admin Preset/);
    fireEvent.click(presetBtn);

    expect(emailInput.value).toBe('officer@college.edu');
    expect(passwordInput.value).toBe('Officer@123');
  });
});
