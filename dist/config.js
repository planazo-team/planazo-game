"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.config = void 0;
function leerEntero(valor, porDefecto) {
    const n = Number(valor);
    return Number.isInteger(n) && n > 0 ? n : porDefecto;
}
exports.config = {
    serviceName: process.env.SERVICE_NAME ?? 'planazo-game',
    port: leerEntero(process.env.PORT, 3003),
    gatewayKey: process.env.GATEWAY_KEY,
    busUrl: process.env.BUS_URL,
    busChannel: process.env.BUS_CHANNEL ?? 'planazo.events',
    databaseUrl: process.env.DATABASE_URL,
    redisUrl: process.env.REDIS_URL,
    jwtSecret: process.env.JWT_SECRET,
    logLevel: process.env.LOG_LEVEL ?? 'log',
};
//# sourceMappingURL=config.js.map