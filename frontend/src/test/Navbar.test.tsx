import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { Navbar } from '../components/Navbar';
import * as AuthContextModule from '../context/AuthContext';

describe('Navbar Component', () => {
  it('renders brand title and navigation links when logged in as officer', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: {
        id: 1,
        username: 'officer@college.edu',
        email: 'officer@college.edu',
        fullName: 'Prof. Admin Officer',
        role: 'ROLE_OFFICER',
      },
      token: 'mock-jwt-token',
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
      isOfficer: true,
    });

    render(
      <BrowserRouter>
        <Navbar />
      </BrowserRouter>
    );

    expect(screen.getByText('T&P Attendance')).toBeInTheDocument();
    expect(screen.getByText('Recruitment Drives')).toBeInTheDocument();
    expect(screen.getByText('Student Devices')).toBeInTheDocument();
    expect(screen.getByText('Prof. Admin Officer')).toBeInTheDocument();
    expect(screen.getByText('Logout')).toBeInTheDocument();
  });
});
