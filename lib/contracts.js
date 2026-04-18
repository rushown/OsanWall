import { z } from "zod";
export const createMoteRequestSchema = z.object({
    id: z.string().min(3).max(128).regex(/^[a-zA-Z0-9:_-]+$/).optional(),
    authorId: z.string().min(3).max(128).regex(/^[a-zA-Z0-9:_-]+$/),
    text: z.string().min(1).max(240),
    x: z.number().finite(),
    y: z.number().finite(),
    vibe: z.string().min(1).max(40).optional(),
});
export const interactMoteRequestSchema = z.object({
    moteId: z.string().min(3).max(128).regex(/^[a-zA-Z0-9:_-]+$/),
    actorId: z.string().min(3).max(128).regex(/^[a-zA-Z0-9:_-]+$/),
});
