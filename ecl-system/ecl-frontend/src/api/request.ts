import axios, { type AxiosResponse } from 'axios';
import { createMockResponse } from './mockApi';

export interface Result<T = any> {
  code: string;
  message: string;
  data: T;
}

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  adapter: async (config) => {
    if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_API !== 'false') {
      return createMockResponse(config);
    }
    const adapter = axios.getAdapter(axios.defaults.adapter);
    return adapter(config);
  },
});

request.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    const res = response.data;
    if (res.code !== '200') {
      return Promise.reject(new Error(res.message || '请求失败'));
    }
    return response;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export default request;
