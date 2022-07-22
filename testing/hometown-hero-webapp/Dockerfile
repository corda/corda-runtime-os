FROM node:14.17.2-alpine3.13 as build
COPY . /frontend
WORKDIR /frontend
COPY package.json  /frontend/package.json
RUN npm set progress=false
RUN npm install
RUN npm run build

# production environment
FROM nginx:1.16.0-alpine
COPY --from=build /frontend/dist /usr/share/nginx/html
COPY nginx/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]