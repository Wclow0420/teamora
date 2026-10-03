import React from 'react';
import { Redirect } from 'expo-router';

/** The old one-page sign-up. Creating a company now happens in the setup wizard; kept so old links still land. */
export default function Register() {
  return <Redirect href="/setup" />;
}
