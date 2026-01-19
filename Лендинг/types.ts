
import React from 'react';

export interface Message {
  id: string;
  sender: 'user' | 'pioneer-ai';
  text: string;
  timestamp: Date;
}

export interface Feature {
  title: string;
  description: string;
  icon: React.ReactNode;
}

export interface NavItem {
  label: string;
  href: string;
}