# Build stage
FROM node:18-alpine as builder

WORKDIR /build
# 1. 只拷贝 package.json，且不拷贝 package-lock.json（已在 .dockerignore 忽略）
# 这将强制 npm 根据当前平台（Linux musl）重新计算并下载依赖，解决跨平台 Rollup 错误
COPY package.json ./
RUN npm install

# 2. 拷贝源代码
COPY . .
RUN npm run build

# Runtime stage
FROM nginx:alpine

COPY --from=builder /build/dist /usr/share/nginx/html

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 3000

CMD ["nginx", "-g", "daemon off;"]
