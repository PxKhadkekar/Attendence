import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { StatusBadge } from '../components/StatusBadge';

describe('StatusBadge Component', () => {
  it('renders OPEN status badge with pulsing indicator', () => {
    const { container } = render(<StatusBadge status="OPEN" />);
    expect(screen.getByText('OPEN')).toBeInTheDocument();
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders SCHEDULED status badge', () => {
    render(<StatusBadge status="SCHEDULED" />);
    expect(screen.getByText('SCHEDULED')).toBeInTheDocument();
  });

  it('renders FINALIZED status badge', () => {
    render(<StatusBadge status="FINALIZED" />);
    expect(screen.getByText('FINALIZED')).toBeInTheDocument();
  });

  it('renders PRESENT and ABSENT badges', () => {
    const { rerender } = render(<StatusBadge status="PRESENT" />);
    expect(screen.getByText('PRESENT')).toBeInTheDocument();

    rerender(<StatusBadge status="ABSENT" />);
    expect(screen.getByText('ABSENT')).toBeInTheDocument();
  });

  it('renders VERIFIED and MANUAL_OVERRIDE badges', () => {
    const { rerender } = render(<StatusBadge status="VERIFIED" />);
    expect(screen.getByText('VERIFIED')).toBeInTheDocument();

    rerender(<StatusBadge status="MANUAL_OVERRIDE" />);
    expect(screen.getByText('OVERRIDE')).toBeInTheDocument();
  });
});
