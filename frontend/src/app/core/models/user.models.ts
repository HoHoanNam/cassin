/** Khớp com.cassin.user.Role */
export type Role = 'USER' | 'ADMIN';

/** Khớp UserResponse */
export interface User {
  id: number;
  email: string;
  displayName: string;
  role: Role;
  /** Giới thiệu ngắn. Có thể null; bản cũ của backend chưa trả field này nên để optional. */
  bio?: string | null;
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

/** Khớp UpdateProfileRequest: PUT /api/me */
export interface UpdateProfileRequest {
  displayName: string;
  bio: string | null;
}

/** Khớp ChangePasswordRequest: PUT /api/me/password */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** Giới hạn khớp @Size ở backend */
export const PROFILE_LIMITS = {
  displayName: 100,
  bio: 500,
  passwordMin: 8,
  passwordMax: 72,
} as const;
