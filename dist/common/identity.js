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
Object.defineProperty(exports, "__esModule", { value: true });
exports.RolesGuard = exports.Roles = exports.ROLES_KEY = exports.Identity = exports.HEADER_USER_PLACE_ID = exports.HEADER_USER_ROLE = exports.HEADER_USER_ID = void 0;
exports.identidadDesdeRequest = identidadDesdeRequest;
const common_1 = require("@nestjs/common");
const core_1 = require("@nestjs/core");
exports.HEADER_USER_ID = 'x-user-id';
exports.HEADER_USER_ROLE = 'x-user-role';
exports.HEADER_USER_PLACE_ID = 'x-user-place-id';
function primerValor(valor) {
    return Array.isArray(valor) ? valor[0] : valor;
}
function identidadDesdeRequest(request) {
    const userId = primerValor(request.headers[exports.HEADER_USER_ID]);
    const role = primerValor(request.headers[exports.HEADER_USER_ROLE]);
    if (!userId || (role !== 'cliente' && role !== 'negocio')) {
        return undefined;
    }
    const placeId = primerValor(request.headers[exports.HEADER_USER_PLACE_ID]);
    return placeId ? { userId, role, placeId } : { userId, role };
}
exports.Identity = (0, common_1.createParamDecorator)((_data, ctx) => {
    const request = ctx.switchToHttp().getRequest();
    const identidad = identidadDesdeRequest(request);
    if (!identidad) {
        throw new common_1.UnauthorizedException({
            code: 'NO_AUTENTICADO',
            message: 'La petición no trae identidad de usuario (x-user-id / x-user-role).',
        });
    }
    return identidad;
});
exports.ROLES_KEY = 'planazo:roles';
const Roles = (...roles) => (0, common_1.SetMetadata)(exports.ROLES_KEY, roles);
exports.Roles = Roles;
let RolesGuard = class RolesGuard {
    reflector;
    constructor(reflector) {
        this.reflector = reflector;
    }
    canActivate(context) {
        const roles = this.reflector.getAllAndOverride(exports.ROLES_KEY, [
            context.getHandler(),
            context.getClass(),
        ]);
        if (!roles || roles.length === 0) {
            return true;
        }
        const request = context.switchToHttp().getRequest();
        const identidad = identidadDesdeRequest(request);
        if (identidad && roles.includes(identidad.role)) {
            return true;
        }
        throw new common_1.ForbiddenException({
            code: 'ROL_NO_AUTORIZADO',
            message: `Esta operación requiere rol ${roles.join(' o ')}.`,
        });
    }
};
exports.RolesGuard = RolesGuard;
exports.RolesGuard = RolesGuard = __decorate([
    (0, common_1.Injectable)(),
    __metadata("design:paramtypes", [core_1.Reflector])
], RolesGuard);
//# sourceMappingURL=identity.js.map