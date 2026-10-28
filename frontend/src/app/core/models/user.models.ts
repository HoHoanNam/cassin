/** Khớp com.cassin.user.Role */
export type Role = 'USER' | 'ADMIN';

/** Khớp UserResponse */
export interface User {
  id: number;
  email: string;
  displayName: string;
  role: Role;
}

/** Khớp RegisterRequest */
export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

/** Khớp LoginRequest */
export interface LoginRequest {
  email: string;
  password: string;
}

/** Khớp LoginResponse */
export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  /** Số GIÂY token còn hiệu lực. */
  expiresIn: number;
  user: User;
}
