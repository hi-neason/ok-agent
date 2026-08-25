/** Public lazy page loader for the customer-facing conversation module. */
export const customerChatPages = {
  chat: () => import("../chat/CustomerChatPage").then((module) => ({ default: module.CustomerChatPage })),
};
