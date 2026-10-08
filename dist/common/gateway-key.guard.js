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
Object.defineProperty(exports, "__esModule", { value: true });
exports.GatewayKeyGuard = exports.GATEWAY_KEY_TOKEN = exports.GATEWAY_KEY_HEADER = void 0;
const common_1 = require("@nestjs/common");
const core_1 = require("@nestjs/core");
const config_1 = require("../config");
const public_decorator_1 = require("./public.decorator");
exports.GATEWAY_KEY_HEADER = 'x-gateway-key';
exports.GATEWAY_KEY_TOKEN = 'planazo:gatewayKey';
let GatewayKeyGuard = class GatewayKeyGuard {
    reflector;
    gatewayKey;
    constructor(reflector, gatewayKey) {
        this.reflector = reflector;
        this.gatewayKey = gatewayKey === undefined ? config_1.config.gatewayKey : gatewayKey;
    }
    canActivate(context) {
        if (!this.gatewayKey) {
            return true;
        }
        const esPublica = this.reflector.getAllAndOverride(public_decorator_1.IS_PUBLIC_KEY, [
            context.getHandler(),
            context.getClass(),
        ]);
        if (esPublica) {
            return true;
        }
        const request = context.switchToHttp().getRequest();
        const recibida = request.headers[exports.GATEWAY_KEY_HEADER];
        if (recibida === this.gatewayKey) {
            return true;
        }
        throw new common_1.UnauthorizedException({
            code: 'GATEWAY_KEY_INVALIDA',
            message: 'La petición no viene del API Gateway o la clave es incorrecta.',
        });
    }
};
exports.GatewayKeyGuard = GatewayKeyGuard;
exports.GatewayKeyGuard = GatewayKeyGuard = __decorate([
    (0, common_1.Injectable)(),
    __param(1, (0, common_1.Optional)()),
    __param(1, (0, common_1.Inject)(exports.GATEWAY_KEY_TOKEN)),
    __metadata("design:paramtypes", [core_1.Reflector, String])
], GatewayKeyGuard);
//# sourceMappingURL=gateway-key.guard.js.map