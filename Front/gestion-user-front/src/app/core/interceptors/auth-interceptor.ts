import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  
  // 🔥 IGNORER L'ENDPOINT GITHUB - PAS DE TOKEN
  if (req.url.includes('/api/users/me/github-token')) {
    console.log('⏭️ Interceptor ignoré pour:', req.url);
    return next(req);
  }
  
  const token = auth.getToken();

  if (token) {
    const cloned = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
    console.log('🔑 Token ajouté pour:', req.url);
    return next(cloned);
  }

  console.log('⏭️ Aucun token pour:', req.url);
  return next(req);
};