# Production Dockerfile for PowerTrack Cloud Backend & Dashboard
FROM node:20-alpine

WORKDIR /app

# Copy package descriptors
COPY package*.json ./

# Install production dependencies
RUN npm ci --only=production

# Copy backend source
COPY . .

# Set production environment variables
ENV NODE_ENV=production
ENV PORT=5000
ENV DB_PATH=/data/powertrack.sqlite

# Create persistent storage directory
RUN mkdir -p /data

EXPOSE 5000

CMD ["node", "src/server.js"]
