"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
require("reflect-metadata");
const common_1 = require("@nestjs/common");
const core_1 = require("@nestjs/core");
const app_module_1 = require("./app.module");
const config_1 = require("./config");
function nivelesDeLog(nivel) {
    const orden = ['debug', 'log', 'warn', 'error'];
    const desde = orden.indexOf(nivel);
    return desde === -1 ? orden.slice(1) : orden.slice(desde);
}
async function bootstrap() {
    const app = await core_1.NestFactory.create(app_module_1.AppModule, { logger: nivelesDeLog(config_1.config.logLevel) });
    app.enableShutdownHooks();
    await app.listen(config_1.config.port);
    common_1.Logger.log(`${config_1.config.serviceName} escuchando en :${config_1.config.port}`, 'Bootstrap');
}
bootstrap().catch((err) => {
    common_1.Logger.error(err instanceof Error ? (err.stack ?? err.message) : String(err), 'Bootstrap');
    process.exit(1);
});
//# sourceMappingURL=main.js.map