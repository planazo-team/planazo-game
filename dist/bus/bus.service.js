"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
var __param = (this && this.__param) || function (paramIndex, decorator) {
    return function (target, key) { decorator(target, key, paramIndex); }
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
var BusService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.BusService = exports.BUS_CHANNEL_TOKEN = exports.BUS_URL_TOKEN = void 0;
const common_1 = require("@nestjs/common");
const node_crypto_1 = require("node:crypto");
const ioredis_1 = __importDefault(require("ioredis"));
const config_1 = require("../config");
exports.BUS_URL_TOKEN = 'planazo:busUrl';
exports.BUS_CHANNEL_TOKEN = 'planazo:busChannel';
let BusService = BusService_1 = class BusService {
    logger = new common_1.Logger(BusService_1.name);
    cliente;
    avisoSinBus = false;
    busUrl;
    canal;
    constructor(busUrl, canal) {
        this.busUrl = busUrl === undefined ? config_1.config.busUrl : busUrl;
        this.canal = canal ?? config_1.config.busChannel;
    }
    async publish(type, payload, topics) {
        const sobre = {
            id: (0, node_crypto_1.randomUUID)(),
            type,
            topics,
            payload,
            at: Date.now(),
        };
        if (!this.busUrl) {
            if (!this.avisoSinBus) {
                this.logger.warn('BUS_URL no está definida: los eventos no se publican (modo sin bus).');
                this.avisoSinBus = true;
            }
            this.logger.debug(`Evento descartado ${type} -> ${topics.join(',')}`);
            return;
        }
        const redis = this.obtenerCliente(this.busUrl);
        await redis.publish(this.canal, JSON.stringify(sobre));
        this.logger.debug(`Publicado ${type} (${sobre.id}) -> ${topics.join(',')}`);
    }
    obtenerCliente(url) {
        if (!this.cliente) {
            this.cliente = new ioredis_1.default(url, {
                lazyConnect: true,
                maxRetriesPerRequest: 3,
                enableOfflineQueue: true,
            });
            this.cliente.on('error', (err) => {
                this.logger.error(`Error en la conexión con el bus: ${err.message}`);
            });
        }
        return this.cliente;
    }
    async onModuleDestroy() {
        if (this.cliente) {
            try {
                await this.cliente.quit();
            }
            catch {
                this.cliente.disconnect();
            }
            this.cliente = undefined;
        }
    }
};
exports.BusService = BusService;
exports.BusService = BusService = BusService_1 = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, common_1.Optional)()),
    __param(0, (0, common_1.Inject)(exports.BUS_URL_TOKEN)),
    __param(1, (0, common_1.Optional)()),
    __param(1, (0, common_1.Inject)(exports.BUS_CHANNEL_TOKEN)),
    __metadata("design:paramtypes", [String, String])
], BusService);
//# sourceMappingURL=bus.service.js.map