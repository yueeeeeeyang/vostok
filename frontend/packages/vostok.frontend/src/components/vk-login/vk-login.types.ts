export type VkLoginLayoutMode = 'center' | 'left-card' | 'right-card';

export interface VkLoginFormValue {
  username: string;
  password: string;
  remember: boolean;
}

export interface VkLoginProps {
  layoutMode?: VkLoginLayoutMode;
  backgroundImageUrl?: string;
  sideImageUrl?: string;
  sideImageAlt?: string;
  cardTitle?: string;
  usernameLabel?: string;
  usernamePlaceholder?: string;
  passwordLabel?: string;
  passwordPlaceholder?: string;
  loginButtonText?: string;
  showRegister?: boolean;
  registerText?: string;
  showRemember?: boolean;
  rememberText?: string;
  showForgotPassword?: boolean;
  forgotPasswordText?: string;
  loading?: boolean;
  disabled?: boolean;
  initialValue?: Partial<VkLoginFormValue>;
}
